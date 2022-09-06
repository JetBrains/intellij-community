// "Replace with enhanced 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  void test(int x) {
    <caret>switch (x) {
      case 1: {
        System.out.println("1");
        break;
      }
      case 2:
        if (Math.random() > 0.5) {
          System.out.println("2");
          break;
        } else {
          System.out.println("3");
          break;
        }
    }
  }
}