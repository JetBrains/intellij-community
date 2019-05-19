// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(int x) {
    int/*1*/ x /*2*/= /*3*/switch<caret>/*4*/ (x +/*cond*/ x) {
      case 1 -> {if (true)
        break /*5*/0;
      else
        break 1;
      }
      case 2 -> 2;
      case 3, 4 -> {
        System.out.println("asda");
        break 3;
      }
      default -> 12/*6*/;
    };
  }
}