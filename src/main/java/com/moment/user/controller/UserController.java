package com.moment.user.controller;


import java.io.File;
import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.servlet.ModelAndView;

import com.alibaba.fastjson.JSONObject;
import com.moment.common.domain.CurrentUser;
import com.moment.common.domain.JsonResult;
import com.moment.common.util.File2MultipartFile;
import com.moment.common.util.IDUtil;
import com.moment.common.util.PicCropper;
import com.moment.common.util.RegexValidateUtil;
import com.moment.grade.domain.GradeVO;
import com.moment.grade.service.GradeService;
import com.moment.pic.domain.Cropper;
import com.moment.pic.domain.PicVO;
import com.moment.pic.service.PicService;
import com.moment.user.domain.UserVO;
import com.moment.user.service.UserService;
import com.qiniu.http.Response;


@Controller
@RequestMapping("/user")
public class UserController {
	@Autowired
	private UserService service;
	@Autowired
	private GradeService gservice;
	@Autowired
	private PicService pservice ;
		
	@RequestMapping("/checkAccount")
	public @ResponseBody JsonResult checkAccount(String account,JsonResult jsonResult){
			UserVO user=null;
			System.out.println(account);
			try {
				user = service.getUserByAccount(account);
				System.out.println(user);
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(user!=null){
				jsonResult.setMsg("该账号已经被注册");
				jsonResult.setStatus(0);
			}else{
				jsonResult.setStatus(1);
			}
		    return jsonResult;
		    
	}
	@RequestMapping("/doregister")
	public ModelAndView doRegister(HttpServletRequest request,UserVO user){
		ModelAndView mv = new ModelAndView();
		
		//获取用户的账号
		String account = user.getAccount();
		//用正则表达式检查用户输入的内容是否是邮箱或手机号
		if(RegexValidateUtil.checkEmail(account)||RegexValidateUtil.checkMobileNumber(account)){
			if(RegexValidateUtil.checkEmail(account)){
				user.setEmail(account);
			}else{
				user.setPhonum(account);
			}
		}else{
			mv.setViewName("user/register");
			request.setAttribute("msg", "您输入的邮箱地址或手机号码格式有误！");
			return mv;
		}
		
		//获取用户两次输入的密码
		String password = user.getPassword();
		System.out.println("用户第一次输入的密码为："+password);
		String confirmpwd = request.getParameter("confirmpwd");
		System.out.println("用户第二次输入的密码为："+confirmpwd);
		if(!password.equals(confirmpwd)){
			mv.setViewName("user/register");
			request.setAttribute("msg", "您两次输入的密码不一致！");
			return mv;
		}
		
		//获取用户输入的验证码
		String code = request.getParameter("code");
		System.out.println("用户输入的验证码："+code);
		//获取的验证码
		String sessionCode = (String)request.getSession().getAttribute("sessionCode");
		System.out.println("客户端保存的验证码："+sessionCode);
		if(sessionCode.equalsIgnoreCase(code)){
			mv.setViewName("user/login");
			request.setAttribute("msg", "注册成功，请登录！");
			try {
				//设置默认用户头像
				user.setImg("http://ooys7gpai.bkt.clouddn.com/8e5ff227-0e2f-4dec-97a4-b623b9a12596");
				//设置用户名为随机的8位数
				user.setName(IDUtil.uuid().substring(0,7));
				service.addUser(user);
			} catch (Throwable e) {
				e.printStackTrace();
				mv.setViewName("user/register");
		        request.setAttribute("msg", "注册失败！");
			}
		}else{
			mv.setViewName("user/register");
			request.setAttribute("msg", "验证码错误！");
		}
		return mv;
	}
	
	@RequestMapping("/login")
	public String login(){
		return "user/login";
	}
	
	@RequestMapping("/dologin")
	public @ResponseBody JsonResult doLogin(HttpServletRequest request,UserVO user,HttpSession session,HttpServletResponse response,JsonResult jsonResult,String remember){
		UserVO user1=null;
		try {
			user1 = service.checkLogin(user.getAccount(), user.getPassword());
		} catch (Throwable e) {
			e.printStackTrace();
		}
		if(user1!=null){//登录成功
			//获取用户等级
			GradeVO grade=null;
			try {
				grade = gservice.getGradeById(user1.getGradeid());
			} catch (Throwable e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			if(grade!=null){
				//将用户等级存储进session
				session.setAttribute("grade", grade);
			}else{
				//否则设用户等级为LV0
				try {
					session.setAttribute("grade", gservice.getGradeById(1));
				} catch (Throwable e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			//设置当前用户到session
			session.setAttribute("user",user1);
			//如果用户设置了记住密码，保持密码在cookie中30天
			if("remember".equalsIgnoreCase(remember)){
				Cookie cookie2=new Cookie("account", user.getAccount());
				Cookie cookie1=new Cookie("password", user.getPassword());
				cookie1.setMaxAge(60*60*24*30);
				cookie2.setMaxAge(60*60*24*30);
				response.addCookie(cookie1);
				response.addCookie(cookie2);
				
			}
		    //跳转到后台主页，返回验证成功的消息给ajax
			jsonResult.setMsg("登陆成功");
			jsonResult.setStatus(1);

			System.out.println(user1+"登陆了");
		    return jsonResult;
		    
		    
		}else{//登录失败
			jsonResult.setMsg("登录失败");
			jsonResult.setStatus(0);
			System.out.println("登陆失败");
			return jsonResult;
		}
	}
	@RequestMapping("/index")
	public String index(){
		return "user/index";
	}
	
	@RequestMapping("/setting")
	public String setting(){
		return "user/setting";
	}
	@RequestMapping("/center")
	public String center(HttpSession session){
		CurrentUser cuser = CurrentUser.getInstance() ;
		int cuid = cuser.getUserId() ;
		List<PicVO> list = null ;
		try {
			list = pservice.getUserPicList(cuid) ;
			session.setAttribute("cuserList", list);
		} catch (Throwable e) {
			e.printStackTrace();
		}
		return "user/center";
	}
	@RequestMapping("/collect")
	public String collect(){
		return "user/collect";
	}
	@RequestMapping("/logout")
	public String logout(HttpSession session){
		session.invalidate();
		return "redirect:login.action";
	}
	@RequestMapping("/list")
	public String list(HttpSession session,HttpServletRequest request){
		System.out.println("uri:"+request.getRequestURI());
		return "user/list";
	}
	/*@RequestMapping("/doedit")
	public ModelAndView doAdd() throws Throwable{
		ModelAndView model=new ModelAndView("user/list");
		System.out.println("112232123");
		UserVO user = new UserVO();
		user.setEmail("eamil");
		user.setPassword("123");
		user.setName("aaa");
		int a=service.addUser(user);
		System.out.println(a);
		return model;
	}*/
	//上传头像
	@Transactional
	@RequestMapping("/doupload")
	public @ResponseBody JsonResult doUpload(MultipartFile file,HttpSession session,JsonResult result,String imgdata) throws Throwable {
			System.out.println(imgdata);
			System.out.println("裁剪前："+file.getSize());
			Cropper cropper=JSONObject.parseObject(imgdata,Cropper.class);
			
			//头像大小不得超过1024kb
			if(file.getSize()>512000){
				result.setMsg("头像大小不得超过500k");
				result.setStatus(0);
			}else{
				String originName=file.getOriginalFilename();
				String format=originName.substring(originName.lastIndexOf('.'));
				System.out.println(format);
				if((!".jpeg".equals(format))&&(!".jpg".equals(format))&&(!".png".equals(format))&&(!".gif".equals(format))){
					result.setMsg("图片格式须为jpeg,jpg,png,gif其中一种");
					result.setStatus(0);
				}else{
					//对图片进行裁剪
		            MultipartFile file2=PicCropper.cut(file, cropper.getX(),cropper.getY(),cropper.getWidth(),cropper.getHeight()); 
					byte[] newFile = file2.getBytes();
					System.out.println("裁剪后："+file2.getSize());
					System.out.println(file2);
					String path=service.doUpload(file2.getBytes());
					System.out.println(path);
					if(path!=null){
						result.setMsg(path);
						result.setStatus(1);
					}else{
						result.setMsg("头像更新失败");
						result.setStatus(0);
					}
				}
			}
			return result;
	}
	@RequestMapping("/update")
	public @ResponseBody JsonResult update(UserVO user,HttpSession session,JsonResult result) throws Throwable {
			System.out.println(user);
			int a=service.updateUser(user);
			if(a>0){
				result.setMsg("更新成功");
				result.setStatus(1);
				session.setAttribute("user", service.getUserById(user.getId()));
			}else{
				result.setMsg("更新失败");
				result.setStatus(0);
			}
			return result;
	}
	@RequestMapping("/validate")
	public @ResponseBody JsonResult validate(Integer id,String password,JsonResult result) throws Throwable {
			boolean flag=service.validate(id, password);
			if(flag){
				result.setMsg("密码正确");
				result.setStatus(1);
			}else{
				result.setMsg("密码错误");
				result.setStatus(0);
			}
			return result;
	}

}
