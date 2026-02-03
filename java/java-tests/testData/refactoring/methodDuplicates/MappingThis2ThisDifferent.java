class Mapping1 {
  private int myInt;
  public void <caret>method() {
    Object o = this;
  }
  public void context() {
    Object o = this;
  }
}

class Mapping2 {
  private int myInt;
  public void context() {
    Object o = this;
  }
}
