// "Replace '(FooBar)foo' with 'foobar'" "true-preview"

class FooBar {
  public int baz;

  int method(Object foo) {
    foo = null;
    FooBar foobar = (FooBar)foo;
    return ((FooBar<caret>)foo).baz;
  }
}