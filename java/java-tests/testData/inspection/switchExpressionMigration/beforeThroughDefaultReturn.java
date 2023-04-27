// "Replace with 'switch' expression" "true-preview"

class ThroughUntilDefaultReturn {

  static enum Letter {
    A,
    B,
    C,
    D
  }

  private static int test(Letter letter) {
      switch<caret> (letter) {
        case A:
          return 1;
        default:
        case B:
          return 2;
      }
  }
}