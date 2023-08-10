// "Adapt argument using 'Math.toIntExact()'" "true-preview"
import java.util.*;

class Demo {
  void test(List<?> list) {
    List<Integer> intList = Collections.singletonList(Math.toIntExact(123L));
  }
}
