// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(String s) {
    int result = switch (s) {
        case "a" -> 1;
        case "b" -> 2;
        default -> 0;
    };
  }
}