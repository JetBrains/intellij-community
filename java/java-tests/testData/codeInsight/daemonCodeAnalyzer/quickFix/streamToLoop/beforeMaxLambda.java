// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  private static Optional<String> max(Map<String, List<String>> dependencies, String fruits, Map<String, Integer> weights) {
    return dependencies.get(fruits).stream().m<caret>ax((o1, o2) -> weights.get(o1)-weights.get(o2));
  }
}
