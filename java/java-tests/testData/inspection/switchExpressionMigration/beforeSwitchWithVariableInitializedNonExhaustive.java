// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(String s) {
    int result = 0;
    switch<caret>(s) {
      case "a": result = 1; break;
      case "b": result = 2; break;
    }
  }
}