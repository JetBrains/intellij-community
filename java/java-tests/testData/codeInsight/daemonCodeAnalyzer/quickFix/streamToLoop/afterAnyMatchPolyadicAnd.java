// "Replace Stream API chain with loop" "true-preview"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
  public boolean testCond(List<String> list) {
      if (list.stream().noneMatch(String::isEmpty)) return false;
      for (String s : list) {
          if (s == null) {
              return true;
          }
      }
      return false;
  }
}