// "Make 'r' not final" "false"

class C {
  void m() throws Exception {
    try (AutoCloseable r = null) {
      <caret>r = null;
    }
  }
}