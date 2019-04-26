// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result;
    System.out.println("adasd");
    /*before label*/
      /*after label*/
      /*in switch*/
      result = switch (s) {
          case "a" -> 1;
          case "b" -> throw new NullPointerException();
          default -> 0;
      };
  }
}