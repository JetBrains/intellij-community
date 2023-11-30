class X {

  void foo(X x) {
    <caret>x.foo((x)));
  }

}