// "Move switch branch 'List n && 42 == 42' before 'List n'" "false"
import java.util.List;

class Main {
  void test(Object o) {
    String str = switch (o) {
      case List n -> "List";
      case List n && 42 == 42<caret> -> "List n && 42 == 42";
      default -> "default";
    };
  }
}