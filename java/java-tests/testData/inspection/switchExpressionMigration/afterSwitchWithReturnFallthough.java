// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      return switch (n) {/*1*/ /*2*/
          case 1,/*3*/ 2 ->/*4*/
              /*5*/"b";/*6*/
          case 3, 4 -> "c";
          default -> "?";
      };
  }
}