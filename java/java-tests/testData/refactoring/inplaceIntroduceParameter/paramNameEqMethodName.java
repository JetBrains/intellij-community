class A {
  int f() {
    return 0;
  }

  void m() {
    f<caret>();
    f();
  }
}