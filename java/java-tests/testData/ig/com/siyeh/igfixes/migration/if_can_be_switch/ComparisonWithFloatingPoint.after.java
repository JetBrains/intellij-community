class Test {
  String test(double d) {
      <caret>return switch (d) {
          case 1.5 -> "one and half";
          case 2.5 -> "two and half";
          default -> "other";
      };
    }
}
