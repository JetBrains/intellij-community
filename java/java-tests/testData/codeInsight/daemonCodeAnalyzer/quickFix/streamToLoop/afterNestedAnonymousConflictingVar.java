// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.Predicate;
import java.util.stream.Stream;

public class Main {

  private static long test(Map<String, List<String>> strings) {
      long sum = 0;
      for (Map.Entry<String, List<String>> e : strings.entrySet()) {
          if (!e.getKey().isEmpty()) {
              long l = e.getValue().stream().filter(new Predicate<String>() {
                  @Override
                  public boolean test(String s) {
                      return e.getKey().equals(s);
                  }
              }).count();
              sum += l;
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