// "Remove left side of assignment" "true-preview"

class FooBar {
  public int baz;

  {
    baz = <caret>bar();
  }
  
  void bar() {}
}