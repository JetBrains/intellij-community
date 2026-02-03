class MyClass {
  static class MyException extends Exception { }

  void m() {
    try { } catch (RuntimeException | MyException<caret> e) { }
  }
}