// "f false" "true"
public class Test {
  void foo(boolean <caret>f){
    if (f) {
      Syste.out.print(f);
    }
  }
  void bar(){foo(false);}
  void bar1(){foo(false);}
}