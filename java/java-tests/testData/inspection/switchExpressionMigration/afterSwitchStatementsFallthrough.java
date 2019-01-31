// "Replace with enhanced 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      switch (n) {
          case 3, 1 -> System.out.println("a");
          case 5, 6, 7 -> System.out.println("a");
          case 2, 4 -> System.out.println("b");
      }
  }
}