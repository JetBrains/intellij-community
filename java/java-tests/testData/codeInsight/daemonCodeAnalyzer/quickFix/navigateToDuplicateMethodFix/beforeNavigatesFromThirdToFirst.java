// "Navigate to duplicate method" "true"

public class MyClass {
  public void foo() {}
  public void foo() {}
  public void foo<caret>() {}
}