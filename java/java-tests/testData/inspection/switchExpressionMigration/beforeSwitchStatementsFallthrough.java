// "Replace with enhanced 'switch' statement" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    switch<caret> (n) {
      case 3:
      case 1:
        System.out.println("a");
        break;
      case 5:
      case 6, 7:
        System.out.println("a");
        break;
      case 2:
      case 4:
        System.out.println("b");
        break;
    }
  }
}