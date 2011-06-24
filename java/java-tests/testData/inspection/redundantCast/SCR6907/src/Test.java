
public class Test {
  void foo(String msg){}
  void foo(Object o){}

  void method(){
    foo((String)null);
  }
}
