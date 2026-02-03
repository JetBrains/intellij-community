class X {
  void test() {
    boolean x = false;
    boolean y = true;
    foo(x, !y);
  }

  void foo(boolean a, boolean b) {}
}