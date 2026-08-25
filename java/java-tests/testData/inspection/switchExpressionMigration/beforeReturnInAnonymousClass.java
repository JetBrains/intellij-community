// "Replace with 'switch' expression" "true-preview"
import java.util.concurrent.*;

class SwitchExpressionMigration {
  static int getId(String name) {
    <caret>switch (name) {
      case "value1":
        return 1;
      case "value2":
        Callable<Integer> callable = new Callable<>() {
          @Override
          public Integer call() {
            return 2;
          }
        };
        return 2;
      default:
        return 3;
    }
  }
}