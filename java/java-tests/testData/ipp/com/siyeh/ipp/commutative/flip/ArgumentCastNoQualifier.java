class X {

  void method(Object o) {
    <caret>foo((X)o));
  }

  void foo(X x) {

  }

}