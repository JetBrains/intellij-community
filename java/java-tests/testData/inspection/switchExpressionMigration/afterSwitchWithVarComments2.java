// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
    private static void m(int i) {
      String a = switch (i) {
          case 0 -> "asd";
          //                switch (1) {
          /*between*/
          default -> "asfdg";
      };
        /*label*/
    }
}