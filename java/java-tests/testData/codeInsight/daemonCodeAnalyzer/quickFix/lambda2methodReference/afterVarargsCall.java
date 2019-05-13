// "Replace lambda with method reference" "true"
class Example {
  public void m(String... s) {
  }

  {
    Runnable r = this::m;
  }
}