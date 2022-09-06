// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  enum X {A, B}

  int test(X x) {
      return switch (x) {
          case A -> 1;
          case B -> 2;
      };
  }
}