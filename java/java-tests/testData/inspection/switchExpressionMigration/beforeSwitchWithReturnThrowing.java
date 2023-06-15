// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    switch<caret> (n) {
      case 1:
        return "a";
      case 2:
        throw new NullPointerException();
    }
    return "?";
  }
}