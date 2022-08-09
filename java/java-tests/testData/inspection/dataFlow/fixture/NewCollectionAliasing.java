// IDEA-299423
import java.util.*;

class Test {
  private static void exampleThree() {
    final Queue<String> queue1 = new ArrayDeque<>(List.of("foo1", "bar1"));
    final Queue<String> queue2 = new ArrayDeque<>(List.of("foo2", "bar2"));
    final Queue<String> queue3 = new ArrayDeque<>(List.of("foo3", "bar3"));

    if(!queue1.isEmpty() && !queue2.isEmpty() && !queue3.isEmpty()) {
      final String poll1 = queue1.poll();
      final String poll2 = queue2.poll();
      final String poll3 = queue3.poll();
      // NPE warning for poll2.getBytes() and poll3.getBytes() because poll2 and poll3 might be null
      if (poll1.getBytes().length == poll2.getBytes().length || poll3.getBytes().length != 0) {
        // ...
      }
    }
  }
}