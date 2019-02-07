// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      /*1*/
      /*2*/
      /*4*/
      /*5*/
      /*6*/
      return switch (n +/*3*/ n) {
          /*7*/
          /*8*/
          /*9*/
          case 1 -> "a";
          case 2 -> "b";
          default -> "?"/*10*/ + "!";
      };
  }
}