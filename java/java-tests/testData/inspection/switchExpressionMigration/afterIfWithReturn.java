// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      /*1*/
      /*2*/
      /*3*/
      /*4*/
      /*5*/
      /*7*/
      /*8*/
      /*9*/
      return switch (n) {
          /*6*/
          case 0 +/*cond*/ 1 -> "foo";
          /*10*/
          /*11*/
          default -> "bar";
      };
  }
}