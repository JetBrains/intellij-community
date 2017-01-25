// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Stream;

public class Main {

  private static long test(Map<String, List<String>> strings) {
      long sum = 0L;
      for (Map.Entry<String, List<String>> s : strings.entrySet()) {
          if (!s.getKey().isEmpty()) {
              long count = s.getValue().stream().filter(sx -> s.getKey().equals(sx)).count();
              sum += count;
          }
      }
      return sum;
  }

  public static void main(String[] args) {
    boolean x = Stream.of(1, 2, 3).anyMatch(Objects::nonNull);
    Map<String, List<String>> map = new HashMap<>();
    map.put("", Arrays.asList("", "a", "b"));
    map.put("a", Arrays.asList("", "a", "b", "a"));
    map.put("b", Arrays.asList("", "a", "b"));
    map.put("c", Arrays.asList("", "a", "b"));
    System.out.println(test(map));
  }
}