// "Replace with 'switch' expression" "true-preview"
import java.util.concurrent.*;

class SwitchExpressionMigration {
  static int getId(String name) {
      return switch (name) {
          case "value1" -> 1;
          case "value2" -> {
              Callable<Integer> callable = new Callable<>() {
                  @Override
                  public Integer call() {
                      return 2;
                  }
              };
              yield 2;
          }
          default -> 3;
      };
  }
}