// "f true" "true"
public class Test {
  void foo(boolean... <caret>f){
    if (f[0]) {
      System.out.println("hello");
    } else {
      System.out.println("goodbye");
    }
  }
  void bar(){foo(true);}
  void bar1(){foo(true);}
}