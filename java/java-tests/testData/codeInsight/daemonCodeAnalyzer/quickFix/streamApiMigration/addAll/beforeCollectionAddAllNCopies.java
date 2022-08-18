// "Replace with collect" "true-preview"
import java.util.*;

public class Test {
  List<String> test(int[] repeats) {
    List<String> result = new ArrayList<>();
    for (int val : re<caret>peats) {
      result.addAll(Collections.nCopies(val, String.valueOf(val)));
    }
    return result;
  }
}
