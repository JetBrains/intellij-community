// "Adapt argument using 'Math.toIntExact()'" "true-preview"
import java.util.*;

class Demo {
  void test(List<?> list) {
    List<Integer> intList2 = <caret>Collections.unmodifiableList(Collections.singletonList(123L));
  }
}
