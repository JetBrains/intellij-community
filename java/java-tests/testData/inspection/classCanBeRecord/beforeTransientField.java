// "Convert to record class" "false"
class <caret>Scratch {
  private transient final Object foo;

  public Scratch(final Object foo) {
    this.foo = foo;
  }

  public Object foo() {
    return foo;
  }
}