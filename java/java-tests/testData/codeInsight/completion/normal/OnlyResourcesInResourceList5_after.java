class MyClass {
  void f() {
    String response;
    AutoCloseable resource;
    try (resource<caret>) {
    }
  }
}