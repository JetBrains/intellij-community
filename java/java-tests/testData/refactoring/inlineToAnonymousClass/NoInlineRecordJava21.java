class A {
  private R r = new R<caret>();
}
record R() {

  public String toString() {
    return "A";
  }
}