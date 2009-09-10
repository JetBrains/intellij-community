class Mapping {
  public void <caret>method() {
    Mapping m2 = this;
  }

  public void context() {
    Mapping m2 = new Mapping();
  }
}
