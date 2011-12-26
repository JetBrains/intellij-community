class MyClass {
  static class MyResource implements AutoCloseable { }

  void f(MyResource resource) {
    try (final MyResource c = resource<caret>) {
    }
  }
}