// "Show 'foo()' duplicates|->Line #5" "true"

public class MyClass {
  public void foo() {}
  public void <selection><caret>foo</selection>() {}
  public void foo() {}
}