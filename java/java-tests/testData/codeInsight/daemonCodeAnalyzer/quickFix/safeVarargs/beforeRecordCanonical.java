// "Annotate as @SafeVarargs" "true"
record Rec<T>(T... args) {
  public R<caret>ec(T... args) {
    this.args = args;
  }
}