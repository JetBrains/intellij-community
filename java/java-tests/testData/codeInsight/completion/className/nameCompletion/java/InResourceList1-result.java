class MyClass {
  void f() {
    try (AutoCloseable<caret>) {
    }
  }
}