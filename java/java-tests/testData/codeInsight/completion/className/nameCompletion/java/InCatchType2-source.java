class MyClass {
  static class MyException extends Exception { }

  void m() {
    try { } catch (My<caret> e) { }
  }
}