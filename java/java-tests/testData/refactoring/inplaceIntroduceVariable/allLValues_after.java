class C {
  static class X {
    static int y;
  }

  void test() {
      int y = X.y;
      y = 10;
    System.out.println("hello");
    y = 15;
  }
}