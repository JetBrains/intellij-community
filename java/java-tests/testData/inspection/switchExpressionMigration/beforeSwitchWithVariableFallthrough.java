// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result;
    switch<caret>(s) {
      case "c":
      case "a": result = 1; break;
      case "b": result = 2; break;
      default: result = 0;
    }
  }
}