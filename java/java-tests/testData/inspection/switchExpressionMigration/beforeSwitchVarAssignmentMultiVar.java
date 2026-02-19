// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    String s = "foo";
    int x = 5;
    System.out.println(s);

    switch<caret> (x) {
      case 1: s = "bar";break;
      case 2: s = "baz";break;
      default: s = "zuq";break;
    }
  }
}