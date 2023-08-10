// "Replace lambda with method reference" "false"
class Example {
  private void m() { }

  {
    Example e = null;
    Runnable unusedRunnable = () -> <caret>e.m();
  }
  
  interface I {
    void n(Example e);
  }
}