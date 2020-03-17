class Test {
  interface X{}

  void test(X x1, X x2, boolean b) {
    if (x1 == null && b) {
      x1 = x2;
    }
    X x3 = x1 != null ? x1 : x2;
    System.out.println(x3.hashCode());
  }
}