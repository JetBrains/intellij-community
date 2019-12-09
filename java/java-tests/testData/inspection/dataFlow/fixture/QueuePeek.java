import java.util.*;
import java.util.concurrent.*;
import org.jetbrains.annotations.*;

class ThisAsVariable {
  // IDEA-219807 'Method invocation may produce NullPointerException' false positive for ArrayDeque.pollFirst
  void checkPollFirst(ArrayDeque<String> stack, String symbol) {
    if (!stack.isEmpty()) {
      System.out.println(stack.pollFirst().toString());
      System.out.println(stack.pollFirst().<warning descr="Method invocation 'toString' may produce 'NullPointerException'">toString</warning>());
    }
  }

  void check(Queue<String> queue) {
    if(!queue.isEmpty()) {
      System.out.println(queue.peek().length() + queue.peek().length());
    }
  }

  void checkPoll(Queue<String> queue) {
    if(!queue.isEmpty()) {
      System.out.println(queue.poll().length() + queue.poll().<warning descr="Method invocation 'length' may produce 'NullPointerException'">length</warning>());
    }
  }

  void notCheck(Queue<String> queue) {
    System.out.println(queue.peek().<warning descr="Method invocation 'trim' may produce 'NullPointerException'">trim</warning>());
  }

  void checkGetFirstPollFirst(Deque<String> queue) {
    if ("bar".equals(queue.getFirst())) {
      String first = queue.pollFirst();
      System.out.println(first.toString());
    }
  }
}
