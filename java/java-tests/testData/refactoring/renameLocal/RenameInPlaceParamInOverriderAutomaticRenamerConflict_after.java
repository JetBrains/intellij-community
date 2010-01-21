public class Test {
  int myI;
  void foo(int i){
    myI = i;
  }
}

class TestImpl extends Test {
  void foo(int i){
    super.foo(i);
    int pp = 0;
    System.out.println(pp);
  }
}