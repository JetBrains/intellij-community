// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      /*5*/
      return switch (n) {
          /*3*/
          /*4*/
          /*6*/
          /*1*/
          /*2*/
          case 1, 2 -> "b";
          case 3, 4 -> "c";
          default -> "?";
      };
  }
}