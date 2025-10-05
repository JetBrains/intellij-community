// "Show 'foo()' duplicates|->Line #4" "true"

public class MyClass {
  public void <selection><caret>foo</selection>() {}
  public void foo() {}
  public void foo() {}
}