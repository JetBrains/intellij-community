class Mapping {
  private int myField;

  public void <caret>method(int p) {
    p++;
  }

  public void context() {
    myField;
  }
}
