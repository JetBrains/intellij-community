class X {
  void foo(Object obj) {}
  void foo(String str) {}

  void test(Object obj, boolean b) {
    foo((Object)(b ? obj : (<error descr="Expression expected">)</error>));
  }
}