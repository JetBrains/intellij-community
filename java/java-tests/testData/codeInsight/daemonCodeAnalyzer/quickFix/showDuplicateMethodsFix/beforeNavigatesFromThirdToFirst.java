// "Show 'foo()' duplicates|->Line #4" "true"

public class MyClass {
  public void foo() {}
  public void foo() {}
  public void foo<caret>() {}
}