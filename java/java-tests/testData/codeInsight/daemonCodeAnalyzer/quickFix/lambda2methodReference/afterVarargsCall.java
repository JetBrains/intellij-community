// "Replace lambda with method reference" "true-preview"
class Example {
  public void m(String... s) {
  }

  {
    Runnable r = this::m;
  }
}