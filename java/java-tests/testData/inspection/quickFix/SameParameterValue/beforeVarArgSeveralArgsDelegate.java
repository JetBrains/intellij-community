// "x 1" "true"
public class Test {
  void foo(int... <caret>x){
    d(true, x);
  }
  void d(boolean b, int[] x) {}
  void bar(){foo(1);}
  void bar1(){foo(1);}
}