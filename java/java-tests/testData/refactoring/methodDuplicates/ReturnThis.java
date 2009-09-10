class Return {
  private int myInt;

  public Return <caret>method() {
    myInt++;
    return this;
  }

  public void context() {
    myInt++;
    Return r = this;
    // Currently the statement below could be replaced, but it's not. Nobody has requested this.
    myInt++;
  }
}