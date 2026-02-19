// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  void test(int x) {
    String s;
    if (Math.random() > 0.5) {
      s = "bar";
    } else {
      s = "foo";
    }
    switch<caret> (x) {
      case 1:s = "baz";break;
      case 2:s = "qux";break;
    }
  }
}