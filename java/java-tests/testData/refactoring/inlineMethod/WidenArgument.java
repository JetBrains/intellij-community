class UnnecessaryDoubleCast {
  public void check() {
    <caret>eq(25, 25.49);
  }

  private static void eq(double expected, double actual) {
    assertDoubleEquals(expected, actual);
  }

  public static void assertDoubleEquals(double v, double v2) {
  }
}