// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result;
    switch<caret>(s) {
      case "a": result = 1; break;
      case "b":
        throw new NullPointerException();
      default: result = 0;
    }
  }
}