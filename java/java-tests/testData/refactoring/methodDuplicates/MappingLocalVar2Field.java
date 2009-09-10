class Mapping {
  private Mapping myMapping;

  public void <caret>method() {
    Mapping m2 = myMapping;
  }

  public void context() {
    Mapping m = new Mapping();
    Mapping m2 = m;
  }
}
