// "f 5" "true"
public class Test {
  void foo(int... <caret>f){
    Syste.out.print(d(f));
  }
  int d(int[] d){}
  void bar(){foo(5);}
  void bar1(){foo(5);}
}