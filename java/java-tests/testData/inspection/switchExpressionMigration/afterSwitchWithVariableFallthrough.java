// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result = switch (s) {
        case "c", "a" -> 1;
        case "b" -> 2;
        default -> 0;
    };
  }
}