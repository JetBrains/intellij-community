// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public boolean testCond(List<String> list) {
      for (String s : list) {
          if (s.isEmpty()) {
              return false;
          }
      }
      return list.stream().anyMatch(Objects::isNull);
  }
}