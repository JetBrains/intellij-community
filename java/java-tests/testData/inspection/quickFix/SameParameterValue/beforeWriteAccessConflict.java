// "f 1" "true"
public class Test {
  void foo(boolean <caret>f){
    f++;
  }
  void bar(){foo(1);}
  void bar1(){foo(1);}
}