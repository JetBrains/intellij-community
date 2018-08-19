// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  private static Optional<String> max(List<?> list) {
      boolean seen = false;
      String best = null;
      for (Object o : list) {
          if (o instanceof String[]) {
              String[] x = (String[]) o;
              String s = x[0];
              if (!seen || s.compareTo(best) > 0) {
                  seen = true;
                  best = s;
              }
          }
      }
      return seen ? Optional.of(best) : Optional.empty();
  }
}
