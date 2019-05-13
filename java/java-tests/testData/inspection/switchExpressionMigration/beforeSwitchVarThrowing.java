// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result;
    switch<caret>(s) {
      case "a": result = 1; break;
      case "b":
        throw new NulPointerException();
      default: result = 0;
    }
  }
}