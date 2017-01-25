// "Replace Stream API chain with loop" "true"

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test {
  static void test(List<String> list) {
      Map<Boolean, Double> map1 = new HashMap<>();
      map1.put(false, 0.0);
      map1.put(true, 0.0);
      for (String s : list) {
          map1.merge(s.isEmpty(), (double) s.length(), Double::sum);
      }
  }
}