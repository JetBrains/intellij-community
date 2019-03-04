// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      /*1*/
      /*3*/
      switch (n +/*cond*/ n) {/*case*/
          case 1:
              System.out.println("a"/*2*/);
              break;
          case 2:
              System.out.println("b");
              break;
      }
  }
}