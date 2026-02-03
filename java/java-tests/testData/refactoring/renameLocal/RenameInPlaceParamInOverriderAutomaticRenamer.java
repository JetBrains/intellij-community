public class Test {
  int myI;
  void foo(int <caret>i){
    myI = i;
  }
}

class TestImpl extends Test {
  void foo(int i){
    super.foo(i);
  }
}