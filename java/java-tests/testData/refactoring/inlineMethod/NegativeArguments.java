class Test {
  void foo(double d1, double d2) {
    System.out.println(d1, d2);
  }

  {
    f<caret>oo(-12.0, 12.0);
  }
}