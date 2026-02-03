class Test {

  static class B {
    int getX(){
      return 42;
    }
  }

  void test(){
    <selection>int x = new Test.B().getX();</selection>
    System.out.println(x);
  }
}