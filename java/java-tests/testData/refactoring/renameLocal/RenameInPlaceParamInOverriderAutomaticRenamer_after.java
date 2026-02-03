public class Test {
  int myI;
  void foo(int pp){
    myI = pp;
  }
}

class TestImpl extends Test {
  void foo(int pp){
    super.foo(pp);
  }
}