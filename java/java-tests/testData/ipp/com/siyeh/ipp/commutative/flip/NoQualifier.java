class X {

  void foo(X x) {
    <caret>foo((/*1*/(new X())));
  }

}