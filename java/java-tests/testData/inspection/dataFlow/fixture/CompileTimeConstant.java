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

  private static final boolean FRONT_DRAW_GRID_LINES = true;
  private static final boolean BACK_DRAW_GRID_LINES  = true;

  void smthComplex(boolean isFrontPage) {
    if (isFrontPage && FRONT_DRAW_GRID_LINES || !isFrontPage && BACK_DRAW_GRID_LINES) {
      System.out.println();
    }
  }

}