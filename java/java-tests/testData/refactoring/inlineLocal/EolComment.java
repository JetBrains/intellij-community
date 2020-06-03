import java.util.*;

public class UnusedReassignmentInLoop {

  static String test(Deque<String> deque) {
    String <caret>value = deque.isEmpty() ? null : deque.peek(); // comment
    return value;
  }
}