class IdentityComplete {
  private int myField;

  public void <caret>method(boolean bp) {
    // method begins
    String /*egg*/ var = /*egg*/"var value"; // inside method
    myField += bp ?/*egg*/ var.length() : /*egg*/ this.hashCode();  /* inside method */
    /* method ends */
  }

  public void context(boolean bp) {
    /* before fragment */
    String var/*egg*/ = "var value"/*egg*/; // inside context
    myField += /*egg*/ bp ? var.length() : this.hashCode(/*egg*/); /* inside context */
    // after fragment
  }
}