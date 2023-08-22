// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(int x) {
    int x = switch<caret> (x) {
      case 1 -> {if (true)
        yield 0;
      else
        yield 1;
      }
      case 2 -> 2;
      case 3, 4 -> {
        System.out.println("asda");
        yield 3;
      }
      default -> 12;
    };
  }
}