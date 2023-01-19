// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  enum X {A, B}

  int test(X x) {
      /* comment 1 */
      // comment 3
      /* comment 4 */
      /* comment 5 */
      return switch (x) {
          case A -> 1;
          case B -> 2;
      };
      /* comment 2 */
  }
}