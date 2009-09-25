// "f false" "true"
public class Test {
  void foo(boolean <caret>f){}
  void bar(){foo(false);}
  void bar1(){foo(false);}
}