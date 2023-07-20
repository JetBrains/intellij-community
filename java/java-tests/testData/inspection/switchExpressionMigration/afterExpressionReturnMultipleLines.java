// "Replace with 'switch' expression" "true-preview"
import java.util.*;

class ExpressionAssignmentMultipleLines {
  private Callable<?> callable;

  enum State {
    EXCEPTIONAL, CANCELLED, INTERRUPTING, INTERRUPTED, NORMAL
  }

  String test(State state, String outcome) {
      return switch (state) {
          case NORMAL -> {
              String t = "";
              yield "[Completed normally]";
          }
          case EXCEPTIONAL -> "[Completed exceptionally: " + outcome + "]";
          case CANCELLED, INTERRUPTING, INTERRUPTED -> "[Cancelled]";
          default -> {
              final Callable<?> callable = this.callable;
              yield (callable == null)
                      ? "[Not completed]" : "[Not completed, task = " + callable + "]";
          }
      };

  }
}