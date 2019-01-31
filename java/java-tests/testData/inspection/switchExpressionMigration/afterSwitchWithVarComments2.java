// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
    private static void m(int i) {
      //                switch (1) {
      String a = switch (i) {
          /*between*/
          case 0 -> "asd";
          default -> "asfdg";
      };
        /*label*/
    }
}