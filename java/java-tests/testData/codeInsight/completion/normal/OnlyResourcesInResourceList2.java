class MyClass {
  static class MyResource implements AutoCloseable { }

  void f() {
    try (final My<caret>) {
    }
  }
}