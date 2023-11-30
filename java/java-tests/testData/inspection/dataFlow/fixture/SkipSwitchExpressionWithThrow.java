class SkipSwitchExpressionWithThrow {
  static boolean test(int x) {
    return switch (x) {
      case 404 -> false;
      case 401 -> throw new RuntimeException();
      case 403 -> throw new IllegalArgumentException();
      default -> throw new IllegalStateException();
    };
  }
}