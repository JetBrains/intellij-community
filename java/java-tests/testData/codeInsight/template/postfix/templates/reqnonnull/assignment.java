class Foo {
  void m(String s) {
    Object obj;
    obj = s.reqnonnull<caret>
  }
}