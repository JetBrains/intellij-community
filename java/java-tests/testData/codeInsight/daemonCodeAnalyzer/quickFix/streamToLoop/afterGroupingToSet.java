// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
      Map<Integer, Set<String>> map = new HashMap<>();
      for (String string : strings) {
          if (string != null) {
              map.computeIfAbsent(string.length(), k -> new HashSet<>()).add(string);
          }
      }
      System.out.println(map);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd"));
  }
}
