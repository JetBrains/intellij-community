// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result = switch (s) {
        case "a" -> 1;
        case -> 2; default -> 0;
    };
  }
}