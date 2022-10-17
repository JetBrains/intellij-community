// "Replace with 'switch' expression" "true"

public class EnhancedSwitchIntentionBug {
  int test(X a, X b) {
    switch (a) {
      case A:
          return switch (b) {
              case A -> 0;
              case B -> 1;
              case C -> 2;
          };
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