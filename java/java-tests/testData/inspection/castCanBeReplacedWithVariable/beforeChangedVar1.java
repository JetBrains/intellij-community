// "Replace '(FooBar)foo' with 'foobar'" "false"

class FooBar {
  public int baz;

  int method(Object foo) {
    FooBar foobar = (FooBar)foo;
    foobar = null;
    return ((FooBar<caret>)foo).baz;
  }
}