class X {
  int f;

  void test(X x) {
    m(x);
  }

  native void m(X x);
}