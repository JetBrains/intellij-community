// "f false" "true"
public class Test {
  void foo(boolean <caret>f, boolean b){
    if (f) {
      Syste.out.print(f);
    }
  }
  void bar(){foo(false, false);}
  void bar1(){foo(false, true);}
}