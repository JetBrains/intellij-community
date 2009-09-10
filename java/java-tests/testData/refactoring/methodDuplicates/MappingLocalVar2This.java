class Mapping {
  public void <caret>method() {
    Mapping m2 = this;
  }

  public void context() {
    Mapping m = new Mapping();
    Mapping m2 = m;
  }
}
