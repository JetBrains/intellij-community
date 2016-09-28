// "Replace lambda with method reference" "true"
class Example {
  public void m() {
  }

  {
      //my comments here
      Runnable r = this::m
  }
}