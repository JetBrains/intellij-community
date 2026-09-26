class X {

  void varargFunc(String s, Object... ss) {}
  {
    varargFunc("hello", new Object[]<caret>{/* 1 */ "World" });
  }
}