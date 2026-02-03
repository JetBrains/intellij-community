// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result;
    System.out.println("asdasd");
      result = switch (s) {
          case "a" -> 1;
          case "b" -> throw new NullPointerException();
          default -> 0;
      };
  }
}