// "Replace with enhanced 'switch' statement" "true-preview"
import java.util.*;

class ExpressionAssignmentMultipleLines {
  private Callable<?> callable;

  enum State {
    EXCEPTIONAL, CANCELLED, INTERRUPTING, INTERRUPTED, NORMAL
  }

  void test(State state, String outcome) {
    String status;
      switch (state) {
          case NORMAL -> status = "[Completed normally]";
          case EXCEPTIONAL -> status = "[Completed exceptionally: " + outcome + "]";
          case CANCELLED, INTERRUPTING, INTERRUPTED -> status = "[Cancelled]";
          default -> {
              final Callable<?> callable = this.callable;
              status = (callable == null)
                      ? "[Not completed]" : "[Not completed, task = " + callable + "]";
          }
      }

  }
}