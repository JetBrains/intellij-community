// "Replace with enhanced 'switch' statement" "true-preview"
import java.util.*;

class ExpressionAssignmentMultipleLines {
  private Callable<?> callable;

  enum State {
    EXCEPTIONAL, CANCELLED, INTERRUPTING, INTERRUPTED, NORMAL
  }

  String test(State state, String outcome) {
      switch (state) {
          case NORMAL -> {
              String t = "";
              return "[Completed normally]";
          }
          case EXCEPTIONAL -> {
              return "[Completed exceptionally: " + outcome + "]";
          }
          case CANCELLED, INTERRUPTING, INTERRUPTED -> {
              return "[Cancelled]";
          }
          default -> {
              final Callable<?> callable = this.callable;
              return (callable == null)
                      ? "[Not completed]" : "[Not completed, task = " + callable + "]";
          }
      }

  }
}