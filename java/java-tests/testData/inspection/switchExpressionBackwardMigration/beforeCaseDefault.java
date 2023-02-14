// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private void matchingSwitchError(Object obj) {
    int i = switch<caret> (obj) {
      case String s -> 0;
      case default -> 1;
    };
  }
}