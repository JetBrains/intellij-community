// "Replace lambda with method reference" "true"
class Example {
  public void m() {
  }

  {
    Runnable r = () -> {
      //my comments here
      m<caret>();
    }
  }
}