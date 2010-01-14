public class Test {
  int myI;
  void foo(int i){
    myI = i;
  }
}

class TestImpl extends Test {
  void foo(int pp){
    super.foo(pp);
  }
}