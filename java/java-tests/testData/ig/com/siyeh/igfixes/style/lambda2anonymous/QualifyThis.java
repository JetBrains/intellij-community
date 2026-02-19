class Test {
  public void m() {
    Runnable r = (<caret>) -> {System.out.println(this);};
    r.run();
  }
}