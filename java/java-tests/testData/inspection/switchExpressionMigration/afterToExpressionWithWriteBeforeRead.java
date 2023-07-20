// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class ExpressionAssignmentMultipleLines {
  private Callable<?> callable;

  enum State {
    EXCEPTIONAL, CANCELLED, INTERRUPTING, INTERRUPTED, NORMAL
  }

  void test(State state, String outcome) {
    String status = switch (state) {
        case NORMAL -> "[Completed normally]";
        case EXCEPTIONAL -> "[Completed exceptionally: " + outcome + "]";
        case CANCELLED, INTERRUPTING, INTERRUPTED -> "[Cancelled]";
        default -> {
            final Callable<?> callable = this.callable;
            status = "2";
            yield (callable == null) ? status + "[Not completed]" : "[Not completed, task = " + callable + "]";
        }
    };

  }
}