// "Remove left side of assignment" "false"

class FooBar {
  public int baz;

  {
    foo(baz = <caret>bar());
  }

  void foo(int i) {}
  
  void bar() {}
}