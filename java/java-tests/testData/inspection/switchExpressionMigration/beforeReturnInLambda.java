// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  static int getId(String name) {
    <caret>switch (name) {
      case "value1":
        return 1;
      case "value2":
        final int temp = Optional.of(2).orElseGet(() -> {
          return 2;
        });
        return temp;
      default:
        return 3;
    }
  }
}