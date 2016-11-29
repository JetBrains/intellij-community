// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
      Map<Integer, Map<Character, Set<String>>> map = new HashMap<>();
      for (String s : strings) {
          map.computeIfAbsent(s.length(), key -> new HashMap<>()).computeIfAbsent(s.charAt(0), k -> new HashSet<>()).add(s);
      }
      System.out.println(map);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd"));
  }
}
