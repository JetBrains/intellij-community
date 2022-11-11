// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  enum X {A, B}

  int test(X x) {
    switch<caret> (x) {
      case A:
        return 1;
      case B:
        return 2;
    }
    throw new IllegalArgumentException();
  }
}