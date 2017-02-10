// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static Map<Boolean, List<String>> test(List<String> strings) {
      Map<Boolean, List<String>> map = new HashMap<>();
      map.put(false, new ArrayList<>());
      map.put(true, new ArrayList<>());
      for (String s : strings) {
          if (!s.isEmpty()) {
              map.get(s.length() > 1).add(s);
          }
      }
      return map;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}