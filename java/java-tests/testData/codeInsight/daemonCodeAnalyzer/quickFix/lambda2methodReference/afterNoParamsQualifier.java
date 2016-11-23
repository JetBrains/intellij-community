// "Replace lambda with method reference (may change semantics)" "true"
class Example {
  public void m() {
  }

  {
    Runnable r = ex()::m;
  }
  
  Example ex() {
    return this;
  }
}