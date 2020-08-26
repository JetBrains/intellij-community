record S(Integer baz) {
  public S(Integer <caret>baz) {
    this.baz = baz;
  }

  public Integer baz() {
    return null;
  }
}