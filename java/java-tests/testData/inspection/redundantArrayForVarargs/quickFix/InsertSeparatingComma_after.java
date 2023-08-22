class X {

  void varargFunc(String s, Object... ss) {}
  {
    varargFunc("hello", /* 1 */ "World");
  }
}