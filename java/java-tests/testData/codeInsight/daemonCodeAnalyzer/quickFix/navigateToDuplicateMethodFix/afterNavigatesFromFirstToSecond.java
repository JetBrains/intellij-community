// "Navigate to duplicate method" "true"

public class MyClass {
  public void foo() {}
  public void <selection><caret>foo</selection>() {}
  public void foo() {}
}