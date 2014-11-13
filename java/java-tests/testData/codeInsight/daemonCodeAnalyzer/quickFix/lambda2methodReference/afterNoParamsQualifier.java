// "Replace lambda with method reference" "true"
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