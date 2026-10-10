class A {
  A(int x) {}

  <caret>A() {
    this(0);
  }

  void f() {}
}
