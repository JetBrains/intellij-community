// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(int x) {
      int x;
      switch (x) {
          case 1:
              if (true) {
                  x = 0;
                  break;
              } else {
                  x = 1;
                  break;
              }
          case 2:
              x = 2;
              break;
          case 3, 4:
              System.out.println("asda");
              x = 3;
              break;
          default:
              x = 12;
              break;
      }
  }
}