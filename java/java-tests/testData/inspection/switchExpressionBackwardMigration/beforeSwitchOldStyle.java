// "Replace with old style 'switch' statement" "false"
import java.util.*;

class SwitchExpressionMigration {
  int foo(int n) {
    return switch<caret> (n) {
      case 1:
        yield 1;
      default:
        yield 0;
    };
  }
}