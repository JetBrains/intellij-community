// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result = switch (s) {
        case "a" -> 1;
        case "b" -> throw new NulPointerException();
        default -> 0;
    };
  }
}