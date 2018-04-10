class C {
  boolean f<caret>oo() {
    return false;
  }

  void bar() {
    boolean a, b;
    a = b = foo();
  }
}
