// "Create method 'fooBar'" "true"
class FooBar {
  {
    Runnable r = this::foo<caret>Bar;
  }
}
