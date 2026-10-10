class A {
  private void f() {}

  private void g(int a) {
    f();
  }

  void use() {
    g(1);
  }
}
