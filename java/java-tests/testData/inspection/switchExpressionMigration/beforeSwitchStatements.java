// "Replace with enhanced 'switch' statement" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    switch<caret> (n) {
      case 1:/*1*/
        System.out.println("a"/*2*/); /*3*/
        break;
      case 2:
        System.out.println("b");
        break;
    }
  }
}