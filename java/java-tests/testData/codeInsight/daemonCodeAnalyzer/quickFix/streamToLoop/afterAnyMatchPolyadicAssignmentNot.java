// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public boolean testCond(List<String> list) {
      boolean b = true;
      for (String s: list) {
          if (s.isEmpty()) {
              b = false;
              break;
          }
      }
      boolean x = b && list.stream().anyMatch(Objects::isNull) && list.size() > 2;
    return x;
  }
}