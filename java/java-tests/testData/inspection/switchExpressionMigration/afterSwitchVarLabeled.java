// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result;
    System.out.println("adasd");
    /*before label*/
      /*after label*/
      result = switch (s) {/*in switch*/
          case "a" -> 1;
          case "b" -> throw new NullPointerException();
          default -> 0;
      };
  }
}