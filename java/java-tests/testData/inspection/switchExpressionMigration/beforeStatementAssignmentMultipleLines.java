// "Replace with enhanced 'switch' statement" "true-preview"
import java.util.*;

class ExpressionAssignmentMultipleLines {
  private Callable<?> callable;

  enum State {
    EXCEPTIONAL, CANCELLED, INTERRUPTING, INTERRUPTED, NORMAL
  }

  void test(State state, String outcome) {
    String status;
    switch<caret> (state) {
      case NORMAL:
        status = "[Completed normally]";
        break;
      case EXCEPTIONAL:
        status = "[Completed exceptionally: " + outcome + "]";
        break;
      case CANCELLED:
      case INTERRUPTING:
      case INTERRUPTED:
        status = "[Cancelled]";
        break;
      default:
        final Callable<?> callable = this.callable;
        status =(callable == null)
                 ? "[Not completed]" : "[Not completed, task = " + callable + "]";
    }

  }
}