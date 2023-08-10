class ImplicitType {
  void test() {
    var x/*<# : ImplicitType #>*/ = someMethod();
  }

  ImplicitType someMethod() {
    return null;
  }
}
