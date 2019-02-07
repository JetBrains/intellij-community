// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m(int x) {
      int y;
      switch (x) {
          case 1:
              y = 1;
              break;
          default:
              y = 0;
              break;
      }
  }
}