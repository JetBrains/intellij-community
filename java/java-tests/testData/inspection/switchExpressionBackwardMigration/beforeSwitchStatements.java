// "Replace with old style 'switch' statement" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
      switch<caret> (n +/*cond*/ n) {
          /*1*/
          case /*case*/1 -> System.out.println("a"/*2*/); /*3*/
          case 2 -> System.out.println("b");
      }
  }
}