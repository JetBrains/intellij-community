// "Replace with 'switch' expression" "true"
import java.util.*;

class SwitchExpressionMigration {
  int test(String s) {
    switch<caret> (s) {
      default /*1*/:
      case /*2*/ "foo" /*3*/:
        return 1;
      case "bar":
        return 2;
    }
  }
}