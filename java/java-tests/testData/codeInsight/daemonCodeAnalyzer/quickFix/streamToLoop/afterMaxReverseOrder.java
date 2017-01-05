// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  private static Optional<String> max(Map<String, List<String>> dependencies, String fruits, Map<String, String> weights) {
      boolean seen = false;
      String best = null;
      for (String s : dependencies.get(fruits)) {
          if (!seen || best.compareTo(s) > 0) {
              seen = true;
              best = s;
          }
      }
      return seen ? Optional.of(best) : Optional.empty();
  }
}
