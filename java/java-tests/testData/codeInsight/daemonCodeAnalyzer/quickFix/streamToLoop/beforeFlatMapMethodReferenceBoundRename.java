// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {

  private static long test(Map<String, List<String>> strings) {
    return strings.entrySet().stream().filter(e -> !e.getKey().isEmpty())
      .flatMap(entry -> entry.getValue().stream().filter(entry.getKey()::equals))
      .c<caret>ount();
  }

  public static void main(String[] args) {
    Map<String, List<String>> map = new HashMap<>();
    map.put("", Arrays.asList("", "a", "b"));
    map.put("a", Arrays.asList("", "a", "b", "a"));
    map.put("b", Arrays.asList("", "a", "b"));
    map.put("c", Arrays.asList("", "a", "b"));
    System.out.println(test(map));
  }
}