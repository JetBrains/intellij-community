// "Annotate as '@SafeVarargs'" "true-preview"
record Rec<T>(T... args) {
  public R<caret>ec(T... args) {
    this.args = args;
  }
}