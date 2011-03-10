class MyClass {
  static class MyException extends Exception { }

  void m() {
    try { } catch (RuntimeException | My<caret> e) { }
  }
}