// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    String s = switch (n) {
        case 0 -> "foo";
        default -> "bar";
    };
      return s;
  }
}