// "Replace with enhanced 'switch' statement" "false"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    switch<caret> (n) {
      case 1:
        System.out.println("a");
      case 2:
        System.out.println("b");
    }
  }
}