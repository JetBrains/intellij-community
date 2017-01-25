// "Replace Stream API chain with loop" "true"

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Main {
  public long test(List<String> list) {
      long count = 0L;
      Set<String> uniqueValues = new HashSet<>();
      for (String s : list) {
          if (uniqueValues.add(s)) {
              count++;
          }
      }
      return count;
  }
}