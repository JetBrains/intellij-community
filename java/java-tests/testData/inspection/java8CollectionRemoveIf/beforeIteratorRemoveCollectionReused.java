// "Replace the loop with Collection.removeIf" "false"
import java.util.*;

public class Main {
  static List<Integer> select(Collection<Integer> input) {
    List<Integer> result = new LinkedList<>(input);
    f<caret>or (Iterator<Integer> iterator = result.iterator(); iterator.hasNext(); ) {
      Integer left = iterator.next();
      if (result.stream().noneMatch(right -> right == left * 2)) {
        iterator.remove();
      }
    }
    return result;
  }
}