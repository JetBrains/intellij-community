class Fun {
  public static final boolean isDebug = true;

  void foo() {
    if (isDebug) {
      System.out.println();
    }
  }
  void fooNegated() {
    if (!isDebug) {
      System.out.println();
    }
  }
}