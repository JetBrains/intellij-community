class Mapping {
  public void <caret>method() {
    Mapping methodVar = new Mapping();
    methodVar.hashCode();
  }

  public void context() {
    Mapping contextVar = new Mapping();
    contextVar.hashCode();
  }
}
