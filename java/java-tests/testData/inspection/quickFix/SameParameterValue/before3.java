// "b false" "true"
public class Test {
  void foo(boolean f, boolean <caret>b){
    if (f) {
      Syste.out.print(f);
    }
  }
  void bar(){foo(false, false);}
  void bar1(){foo(true, false);}
}