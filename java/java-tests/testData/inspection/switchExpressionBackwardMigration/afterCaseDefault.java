// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private void matchingSwitchError(Object obj) {
      int i;
      switch (obj) {
          case String s:
              i = 0;
              break;
          default:
              i = 1;
              break;
      }
  }
}