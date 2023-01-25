// "Make 'VarArgMismatch' take parameter of type 'int...' here" "true-preview"
record VarArgMismatch(int... x) {
  public VarArgMismatch(int<caret>[] x) {
    this.x = x;
  }
}
