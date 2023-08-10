// "Replace '(FooBar)foo' with 'foobar'" "false"

class FooBar {
  public int baz;

  int method(Object foo) {
    FooBar foobar = (FooBar)foo;
    while (baz < 10) {
      baz = ((FooBar<caret>)foo).baz + baz;
      foobar = null;
    }
    return -1;
  }
}