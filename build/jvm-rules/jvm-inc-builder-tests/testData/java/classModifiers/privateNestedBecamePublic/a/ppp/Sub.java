package ppp;
import qqq.Helper;
public class Sub extends Outer { int x = new Helper().f(); }  // private member types are not inherited -> Helper = qqq.Helper
