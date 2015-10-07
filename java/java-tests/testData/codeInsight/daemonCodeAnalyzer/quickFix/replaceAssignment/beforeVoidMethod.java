// "Remove left side of assignment" "true"

class FooBar {
  public int baz;

  {
    baz = <caret>bar();
  }
  
  void bar() {}
}