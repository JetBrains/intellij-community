class Test {
  private boolean notInitial<caret>ized;

  public void foo() {
    if (notInitialized) {
      notInitialized = false;
    }
  }

}