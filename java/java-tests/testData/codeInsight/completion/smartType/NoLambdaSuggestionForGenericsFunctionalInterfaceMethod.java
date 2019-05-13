class Test {
  @FunctionalInterface
  interface GenericOperation {
    <R> R get(R value);
  }

  public static GenericOperation build() {
    return <caret>
  }
}