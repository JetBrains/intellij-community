class IdentityComplete {
  private int myField;

  public void <caret>method(boolean bp) {
    String var = "var value";
    myField += bp ? var.length() : this.hashCode();
  }

  public void context(boolean bp) {
    String var = "var value";
    myField += bp ? var.length() : this.hashCode();
  }
}