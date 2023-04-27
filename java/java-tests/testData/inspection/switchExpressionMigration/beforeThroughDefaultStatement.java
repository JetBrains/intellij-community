// "Replace with enhanced 'switch' statement" "true-preview"

class ThroughUntilDefaultaAssignment {

  static enum Letter {
    A,
    B,
    C,
    D
  }

  private static void test(Letter letter) {
    switch<caret> (letter) {
      case A:
        System.out.println("1");
        break;
      default:
      case B:
          System.out.println(2);
    }
  }
}