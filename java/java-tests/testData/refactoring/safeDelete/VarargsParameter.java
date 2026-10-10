class A {
  void f(int a, String... <caret>rest) {}

  void use() {
    f(1, "a", "b");
  }
}
