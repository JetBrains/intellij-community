// "Replace with collect" "true"
import java.util.*;

class Test {
  List<String> test(List<List<String>> list) {
    List<String> result = new ArrayList<>();
    <caret>for (List<String> strings : list) {
      result.addAll(strings);
    }
    return Collections.unmodifiableList(result);
  }
}