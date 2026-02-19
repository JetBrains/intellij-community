// "Replace with 'switch' expression" "true-preview"

class ThroughUntilDefaultaAssignment {

  static enum Letter {
    A,
    B,
    C,
    D
  }

  private static void test(Letter letter) {
    int i = 1;
    switch<caret> (letter) {
      case A:
        i = 2;
        break;
      case B:
      default:
        i = 3;
    }
  }
}