// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
      Map<Integer, Map<Character, String>> map = new HashMap<>();
      for (String s : strings) {
          if (map.computeIfAbsent(s.length(), k -> new HashMap<>()).put(s.charAt(0), s) != null) {
              throw new IllegalStateException("Duplicate key");
          }
      }
      System.out.println(map);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd"));
  }
}
