// "Replace lambda with method reference" "true-preview"
class Example {
  public void m() {
  }

  {
      //my comments here
      //1
      //2
      Runnable r = this::m
  }
}