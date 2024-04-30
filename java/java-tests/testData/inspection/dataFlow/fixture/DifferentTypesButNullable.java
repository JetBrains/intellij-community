class Test {
  public boolean valuesAreEqual(final String a, final Double b) {
    if (a == null || b == null) {
      return (Object) a == b;
    }
    return false;
  }
}
