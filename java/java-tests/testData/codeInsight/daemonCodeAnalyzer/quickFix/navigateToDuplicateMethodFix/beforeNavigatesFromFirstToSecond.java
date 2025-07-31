// "Navigate to duplicate method" "true"

public class MyClass {
  public void foo<caret>() {}
  public void foo() {}
  public void foo() {}
}