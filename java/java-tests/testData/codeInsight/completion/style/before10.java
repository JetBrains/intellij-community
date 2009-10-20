class A{
 class Constants{
  public static final Constants xxxx;
 }

 public void foo(Constants s){}
 public void foo(boolean bol){}

 {
  foo(<caret>equals);
 }
}