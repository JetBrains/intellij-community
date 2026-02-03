// "Remove left side of assignment" "false"

class FooBar {
  public String baz;

  {
    baz = <caret>bar();
  }
  
  int bar() { return 0;}
}