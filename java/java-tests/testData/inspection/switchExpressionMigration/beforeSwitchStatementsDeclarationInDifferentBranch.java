// "Replace with enhanced 'switch' statement" "false"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    switch<caret> (n) {
      case 1:
        int x = 0;
        System.out.println("a");
        break;
      case 2:
        x = 3;
        System.out.println(x);
        break;
    }
  }
}