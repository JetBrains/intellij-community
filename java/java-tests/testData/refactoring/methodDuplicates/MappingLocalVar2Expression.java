class Mapping {
  private int myInt;

  public void <caret>method() {
    int contextVar = 1;
    myInt += contextVar + 1;
  }

  public void context() {
    int contextVar = 1;
    myInt += contextVar;
  }
}
