// "Annotate as @SafeVarargs" "true"
record Rec<T>(T... args) {
  @SafeVarargs
  public Rec(T... args) {
    this.args = args;
  }
}