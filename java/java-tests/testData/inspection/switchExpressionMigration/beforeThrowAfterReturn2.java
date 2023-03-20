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
    System.out.println(/* comment 1 */"Asd");
    /* comment 2 */
    if (true) {
      // comment 3
      throw /* comment 4 */ new IllegalArgumentException("asd");
    } else {
      /* comment 5 */
      throw new IllegalArgumentException("123");
    }
  }
}