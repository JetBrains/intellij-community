// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    String s = "foo";
    int x = 5;
    System.out.println(s);

      s = switch (x) {
          case 1 -> "bar";
          case 2 -> "baz";
          default -> "zuq";
      };
  }
}