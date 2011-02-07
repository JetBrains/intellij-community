public class Remove1{
  void foo(Object o){}
  void foo(String o){}
  public static class A extends Remove{
   void foo(Object o){
   }
   void foo(String o){
   }
  }

  public static void main(){
    new A().<ref>foo("aaa");
  }
}