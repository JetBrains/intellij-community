// "Replace lambda with method reference" "true"
class Example {
  public void m() {
  }

  {
    Runnable r = () -> <caret>m();
  }
}