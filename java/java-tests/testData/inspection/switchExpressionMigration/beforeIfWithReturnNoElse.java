// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    if<caret>(n == 0) {
      return "foo";
    }
    return "bar";
  }
}