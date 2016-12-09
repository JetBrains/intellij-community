// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public static Optional<String> test(List<String> strings) {
      Comparator<String> comparator = Comparator.naturalOrder();
      boolean seen = false;
      String best = null;
      for (String s : strings) {
          if (!s.isEmpty()) {
              if (!seen || comparator.compare(s, best) > 0) {
                  seen = true;
                  best = s;
              }
          }
      }
      return seen ? Optional.of(best) : Optional.empty();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}