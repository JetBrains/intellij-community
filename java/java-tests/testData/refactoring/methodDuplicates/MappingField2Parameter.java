class Mapping {
  private int myInt;

  public void <caret>method(int p) {
    myInt = p + 1;
  }

  public void context1() {
    myInt = myInt + 1;
  }

  public void context2() {
    myInt = this.myInt + 1;
  }
}
