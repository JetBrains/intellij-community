class BreakDeepInsideSwitchExpression {
  int test(int i) {
    return switch (i) {
      default -> {
        while (true) {
          if (--i < 8) break;
        }
        yield i;
      };
    };
  }
}