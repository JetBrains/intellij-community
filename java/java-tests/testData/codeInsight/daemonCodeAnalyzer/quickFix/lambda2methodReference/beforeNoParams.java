// "Replace lambda with method reference" "true-preview"
class Example {
  public void m() {
  }

  {
    Runnable r = () -> <caret>m();
  }
}