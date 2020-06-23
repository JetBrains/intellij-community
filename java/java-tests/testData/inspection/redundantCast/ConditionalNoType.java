class X {
  void foo(Object obj) {}
  void foo(String str) {}

  void test(Object obj, boolean b) {
    foo((<warning descr="Casting '(b ? obj : ())' to 'Object' is redundant">Object</warning>)(b ? obj : (<error descr="Expression expected">)</error>));
  }
}