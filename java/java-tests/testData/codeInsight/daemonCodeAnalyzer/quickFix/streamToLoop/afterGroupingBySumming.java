// "Replace Stream API chain with loop" "true"

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test {
  static void test(List<String> list) {
      Map<Integer, Double> map4 = new HashMap<>();
      for (String s : list) {
          map4.merge(s.length(), (double) s.length(), Double::sum);
      }
  }
}