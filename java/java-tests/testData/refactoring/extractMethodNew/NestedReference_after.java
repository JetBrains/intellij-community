class Test {

  static class B {
    int getX(){
      return 42;
    }
  }

  void test(){
      int x = newMethod();
      System.out.println(x);
  }

    private int newMethod() {
        int x = new B().getX();
        return x;
    }
}