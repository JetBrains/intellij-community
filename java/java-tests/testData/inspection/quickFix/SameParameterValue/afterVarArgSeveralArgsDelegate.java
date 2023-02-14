// "x 1" "true"
public class Test {
  void foo(){
    d(true, new int[]{1});
  }
  void d(boolean b, int[] x) {}
  void bar(){foo();}
  void bar1(){foo();}
}