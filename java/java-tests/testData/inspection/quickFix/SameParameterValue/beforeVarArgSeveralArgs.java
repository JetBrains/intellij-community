// "f true" "true"
public class Test {
  void foo(int x, boolean... <caret>f){
    if (f[0]) {
      System.out.println("hello");
    } else {
      System.out.println("goodbye");
    }
  }
  void bar(){foo(1, true);}
  void bar1(){foo(2, true);}
}