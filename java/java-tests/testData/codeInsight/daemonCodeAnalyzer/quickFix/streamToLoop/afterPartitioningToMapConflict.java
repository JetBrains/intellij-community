// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
      // and collect
      Map<Boolean, Map<String, Integer>> map = new HashMap<>();
      map.put(false, new HashMap<>());
      map.put(true, new HashMap<>());
      for (String string : strings) {
          String s = string/*trimming*/.trim();
          if (map.get(s.length() /*too big!*/ > 2).put(((UnaryOperator<String>) /* cast is necessary here */ x -> x).apply(s), s.length()) != null) {
              throw new IllegalStateException("Duplicate key");
          }
      }
      System.out.println(map);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e", "e"));
  }
}
