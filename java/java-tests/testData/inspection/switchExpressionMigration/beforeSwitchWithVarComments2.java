// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
    private static void m(int i) {
      String a;
      fo/*label*/:
      switch<caret> (i) {
        case 0:
          a = "asd";
          //                switch (1) {
          break /*between*/ fo;
        default:
          a = "asfdg";
          break;
      }
  }
}