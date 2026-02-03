// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  int foo(int n) {
    return switch<caret> (n) {
      case -> 12;
      default -> 0;
    };
  }
}