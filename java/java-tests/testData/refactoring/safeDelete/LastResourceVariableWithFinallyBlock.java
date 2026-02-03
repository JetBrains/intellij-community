class ARM {
  void f() {
    try (AutoCloseable <caret>r = null) {
    } finally {}
  }
}