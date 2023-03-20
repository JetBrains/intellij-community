// "Replace with 'switch' expression" "true-preview"

class ThroughUntilDefaultaAssignment {

  static enum Letter {
    A,
    B,
    C,
    D
  }

  private static void test(Letter letter) {
    int i = switch (letter) {
        case A -> 2;
        default -> 3;
    };
  }
}