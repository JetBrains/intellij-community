// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  private static String m(int n) {
    String s;
    if<caret>(n == 0) {
      s =  "foo";
    } else {
      s =  "bar";
    }
    return s;
  }
}