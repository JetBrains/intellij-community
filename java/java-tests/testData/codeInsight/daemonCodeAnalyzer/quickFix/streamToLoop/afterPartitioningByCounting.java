// "Replace Stream API chain with loop" "true"

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test {
  static void test(List<String> list) {
      Map<Boolean, Long> map2 = new HashMap<>();
      map2.put(false, 0L);
      map2.put(true, 0L);
      for (String s : list) {
          map2.merge(s.isEmpty(), 1L, Long::sum);
      }
  }
}