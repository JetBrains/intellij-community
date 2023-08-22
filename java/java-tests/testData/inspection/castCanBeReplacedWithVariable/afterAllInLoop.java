// "Replace '(FooBar)foo' with 'foobar'" "true"

class FooBar {
  public int baz;

  void method(Object foo, List<String> texts) {
    for (String text : texts) {
      FooBar foobar = (FooBar)foo;
      baz = foobar.baz;
      foobar = null;
    }
  }
}