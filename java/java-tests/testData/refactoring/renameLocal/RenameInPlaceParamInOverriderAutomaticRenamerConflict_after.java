public class Test {
  int myI;
  void foo(int pp){
    myI = pp;
  }
}

class TestImpl extends Test {
  void foo(int i){
    super.foo(i);
    int pp = 0;
    System.out.println(pp);
  }
}