// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  void test(int x) {
    String s;
    if (Math.random() > 0.5) {
      s = "bar";
    } else {
      s = "foo";
    }
      s = switch (x) {
          case 1 -> "baz";
          case 2 -> "qux";
          default -> s;
      };
  }
}