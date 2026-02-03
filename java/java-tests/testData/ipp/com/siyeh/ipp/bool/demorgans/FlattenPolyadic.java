class A {
  void foo(boolean a, boolean b, boolean c, boolean d) {
    boolean f = a && !(b ||<caret> c || d);
  }
}