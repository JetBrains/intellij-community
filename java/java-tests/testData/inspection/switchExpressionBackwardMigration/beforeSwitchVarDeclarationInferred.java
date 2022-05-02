// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(int x) {
    var y = switch<caret> (x) {
      case 1 -> 1;
      default -> 0;
    };
  }
}