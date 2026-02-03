class X {
  public static String foo(int bar) {
    switch (bar) {
      case 10:
        return "matched<caret>";
      default:
        return "default";
    }
  }
}
