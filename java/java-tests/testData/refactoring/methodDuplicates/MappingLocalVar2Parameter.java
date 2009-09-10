class Mapping {
  public void <caret>method(Mapping methodPar) {
    methodPar.hashCode();
  }

  public void context() {
    Mapping contextVar = new Mapping();
    contextVar.hashCode();
  }
}
