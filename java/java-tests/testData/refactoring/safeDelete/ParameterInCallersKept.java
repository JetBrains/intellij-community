class A {
  private void f(int <caret>a) {}

  private void g(int a) {
    f(a);
  }

  void use() {
    g(1);
  }
}
