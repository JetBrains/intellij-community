// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      return switch/*1*/ (/*2*/n +/*3*/ n) /*4*/ {/*5*/
          /*6*/
          case /*7*/1 ->/*8*/
                  "a";/*9*/
          case 2 -> "b";
          default -> "?"/*10*/ + "!";
      };
  }
}