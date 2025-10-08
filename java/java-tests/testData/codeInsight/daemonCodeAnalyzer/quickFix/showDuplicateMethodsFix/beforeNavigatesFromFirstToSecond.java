// "Show 'foo()' duplicates|->Line #5" "true"

public class MyClass {
  public void foo<caret>() {}
  public void foo() {}
  public void foo() {}
}