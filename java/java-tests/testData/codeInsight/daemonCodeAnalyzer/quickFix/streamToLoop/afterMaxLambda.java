// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  private static Optional<String> max(Map<String, List<String>> dependencies, String fruits, Map<String, Integer> weights) {
      boolean seen = false;
      String best = null;
      for (String s : dependencies.get(fruits)) {
          if (!seen || weights.get(s) - weights.get(best) > 0) {
              seen = true;
              best = s;
          }
      }
      return seen ? Optional.of(best) : Optional.empty();
  }
}
