// "Replace '(FooBar)foo' with 'foobar'" "false"

class FooBar {
  public int baz;

  int method(Object foo, List<String> texts) {
    FooBar foobar = (FooBar)foo;
    for (String text : texts) {
      baz = ((FooBar<caret>)foo).baz;
      foobar = null;
    }
    return -1;
  }
}