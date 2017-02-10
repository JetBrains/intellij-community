// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  private static Optional<String> max(Map<String, List<String>> dependencies, String fruits, Map<String, String> weights) {
      boolean seen = false;
      String best = null;
      for (String s : dependencies.get(fruits)) {
          if (!seen || (s.compareTo(best) < 0 ? -1 : s.compareTo(best) > 0 ? 1 : 0) > 0) {
              seen = true;
              best = s;
          }
      }
      return seen ? Optional.of(best) : Optional.empty();
  }
}
