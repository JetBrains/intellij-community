// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static Map<Integer, List<String>> test(List<String> strings) {
      Map<Integer, List<String>> map = new HashMap<>();
      for (String str : strings) {
          map.computeIfAbsent(str.length(), k -> new ArrayList<>()).add(str);
      }
      return map;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee")));
  }
}