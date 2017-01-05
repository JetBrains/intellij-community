// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
      Map<Boolean, LinkedHashSet<String>> map = new HashMap<>();
      map.put(false, new LinkedHashSet<>());
      map.put(true, new LinkedHashSet<>());
      for (String s : strings) {
          map.get(s.length() > 2).add(s);
      }
      System.out.println(map);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e", "e"));
  }
}
