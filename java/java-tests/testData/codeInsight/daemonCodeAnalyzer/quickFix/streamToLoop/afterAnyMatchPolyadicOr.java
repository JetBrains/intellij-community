// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
  public boolean testCond(List<String> list) {
      if (list.stream().anyMatch(String::isEmpty)) return true;
      for (String s : list) {
          if (s == null) {
              return true;
          }
      }
      return false;
  }
}