import java.util.*;
import java.util.concurrent.*;
import org.jetbrains.annotations.*;

class ThisAsVariable {
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
}
