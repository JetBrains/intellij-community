// "Replace with 'switch' expression" "true-preview"

class ThroughUntilDefaultReturn {

  static enum Letter {
    A,
    B,
    C,
    D
  }

  private static int test(Letter letter) {
      return switch (letter) {
          case A -> 1;
          default -> 2;
      };
  }
}