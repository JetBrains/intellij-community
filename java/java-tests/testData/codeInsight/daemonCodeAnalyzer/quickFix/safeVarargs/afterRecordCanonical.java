// "Annotate as '@SafeVarargs'" "true-preview"
record Rec<T>(T... args) {
  @SafeVarargs
  public Rec(T... args) {
    this.args = args;
  }
}