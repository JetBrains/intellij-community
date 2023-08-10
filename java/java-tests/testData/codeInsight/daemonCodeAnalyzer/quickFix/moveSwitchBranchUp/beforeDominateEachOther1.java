// "Move switch branch 'List i' before 'List n'" "false"
import java.util.List;

class Main {
  void test(Object o) {
    String str = switch (o) {
      case List n -> "List";
      case List i<caret> -> "List";
      default -> "default";
    };
  }
}