class A {
  void f(int <caret>a, int <caret>b, int c) {}

  void use() {
    f(1, 2, 3);
  }
}
