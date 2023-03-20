// "Replace with 'switch' expression" "true"

public class EnhancedSwitchIntentionBug {
  int test(X a, X b) {
    switch (a) {
      case A:
        <caret>switch (b) {
          case A:
            return 0;
          case B:
            return 1;
          case C:
            return 2;
        }
      case B:
        return 3;
      case C:
        return 4;
    }
    return -1;
  }

  enum X {
    A,
    B,
    C,
  }
}