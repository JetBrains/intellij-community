// "Show 'foo()' duplicates|->Line #5" "true"

public class MyClass {
  public <T> void foo(T bar) {}
  public <T> void <selection><caret>foo</selection>(T bar) {}
  public <T> void foo(T bar) {}
}