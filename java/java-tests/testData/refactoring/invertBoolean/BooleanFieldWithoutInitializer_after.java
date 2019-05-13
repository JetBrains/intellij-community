class Test {
  private boolean notInitializedInverted = true;

  public void foo() {
    if (!notInitializedInverted) {
      notInitializedInverted = true;
    }
  }

}