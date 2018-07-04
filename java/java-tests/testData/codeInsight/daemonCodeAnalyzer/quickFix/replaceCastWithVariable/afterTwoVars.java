// "Replace '(FooBar)foo' with 'foobar2'" "true"

class FooBar {
  public int baz;

  int method(Object foo) {
    FooBar foobar = (FooBar)foo;
    foobar = null;
    FooBar foobar2 = (FooBar)foo;
      //comment
      return foobar2.baz;
  }
}