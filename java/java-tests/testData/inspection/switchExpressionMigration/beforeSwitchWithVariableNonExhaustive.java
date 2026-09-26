// "Replace with 'switch' expression" "false"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(String s) {
    int result;
    switch<caret>(s) {
      case "a": result = 1; break;
      case "b": result = 2; break;
    }
  }
}