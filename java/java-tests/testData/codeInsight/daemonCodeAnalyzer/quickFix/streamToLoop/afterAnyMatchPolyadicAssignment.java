// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public boolean testCond(List<String> list) {
      boolean x = false;
      for (String s : list) {
          if (s.isEmpty()) {
              x = list.stream().anyMatch(Objects::isNull);
              break;
          }
      }
      return x;
  }
}