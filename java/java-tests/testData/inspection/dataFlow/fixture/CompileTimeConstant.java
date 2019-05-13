class Fun {
  public static final boolean isDebug = true;
  public static final boolean isProduction = false;
  public static final boolean isDebugInProduction = isDebug && isProduction;

  public static boolean isFeatureEnabled() {
    return true;
  }

  void foo() {
    if (isDebug) {
      System.out.println();
    }
    if (isFeatureEnabled()) {
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