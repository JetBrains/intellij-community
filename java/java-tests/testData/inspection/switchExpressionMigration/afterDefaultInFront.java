// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  int test(String s) {
      return switch (s) { /*1*/
          default /*2*/ /*3*/ -> 1;
          case "bar" -> 2;
      };
  }
}