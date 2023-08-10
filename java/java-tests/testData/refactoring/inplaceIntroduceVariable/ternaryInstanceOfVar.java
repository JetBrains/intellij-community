class X {
  void test() {
    Object x = getXyz();
    var y = x instanceof String ? (<caret>String) x : null;
  }
}