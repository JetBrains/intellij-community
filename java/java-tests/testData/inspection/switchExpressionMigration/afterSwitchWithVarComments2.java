// "Replace with enhanced 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
    private static void m(int i) {
      String a;
      fo:
      //                switch (1) {
      switch (i) {
          /*between*/
          case 0 -> a = "asd";
          default -> a = "asfdg";
      }
  }
}