// "Replace lambda with method reference" "true-preview"
class Example {
  public void m() {
  }

  {
    Runnable r = () -> {
      //my comments here
      m<caret>()
      //1
      //2
      ;
    }
  }
}