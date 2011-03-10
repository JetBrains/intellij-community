class MyClass {
  static class MyResource implements AutoCloseable { }

  void f() {
    try (final MyResource<caret>) {
    }
  }
}