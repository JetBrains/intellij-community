// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class SwitchExpressionMigration {
  static int getId(String name) {
      return switch (name) {
          case "value1" -> 1;
          case "value2" -> {
              final int temp = Optional.of(2).orElseGet(() -> {
                  return 2;
              });
              yield temp;
          }
          default -> 3;
      };
  }
}