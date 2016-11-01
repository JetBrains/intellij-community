// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Stream;

public class Main {

  private static long test(Map<String, List<String>> strings) {
    return strings.entrySet().stream().filter(s -> !s.getKey().isEmpty())
      .mapToLong(e -> e.getValue().stream().filter(sx -> e.getKey().equals(sx)).count())
      .su<caret>m();
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