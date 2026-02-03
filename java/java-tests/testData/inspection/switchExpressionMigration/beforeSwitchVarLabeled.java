// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  private static void m() {
    int result;
    System.out.println("adasd");
    /*before label*/
    foo:/*after label*/
    switch<caret>(s) {/*in switch*/
      case "a": result = 1; break foo;
      case "b":
        throw new NullPointerException();
      default: result = 0;
    }
  }
}