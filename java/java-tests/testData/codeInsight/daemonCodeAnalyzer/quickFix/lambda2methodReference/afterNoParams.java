// "Replace lambda with method reference" "true"
class Example {
  public void m() {
  }

  {
    Runnable r = this::m;
  }
}