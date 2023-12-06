// "Replace with enhanced 'switch' statement" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    switch<caret>(s) {
      case "a":
        throw new NullPointerException();
      default:
        throw new NullPointerException();
    }
  }
}