// "Replace Stream API chain with loop" "true"

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Test {
  static void test(List<String> list) {
      Map<Integer, Integer> map3 = new HashMap<>();
      for (String s : list) {
          String trim = s.trim();
          map3.merge(s.length(), trim.length(), Integer::sum);
      }
  }
}