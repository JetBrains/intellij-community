// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(int x) {
      switch (x) {
          case 1:
              if (true)
                  return 0;
              else
                  return 1;
          case 2:
              return 2;
          case 3, 4:
              System.out.println("asda");
              return 3;
          default:
              return 12;
      }
  }
}