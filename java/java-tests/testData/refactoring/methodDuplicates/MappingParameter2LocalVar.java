class Mapping {
  public void <caret>method() {
    Mapping m = new Mapping();
    Mapping m2 = m;
  }

  public void context(Mapping m) {
    Mapping m2 = m;
  }
}
