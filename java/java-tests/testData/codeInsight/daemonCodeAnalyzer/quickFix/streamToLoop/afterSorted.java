// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  public List<String> testSorted(List<String> list) {
      List<String> toSort = new ArrayList<>();
      for (String s : list) {
          if (s != null) {
              toSort.add(s);
          }
      }
      toSort.sort(null);
      List<String> result = new ArrayList<>();
      Set<String> uniqueValues = new HashSet<>();
      for (String s : toSort) {
          String trim = s.trim();
          if (uniqueValues.add(trim)) {
              result.add(trim);
          }
      }
      return result;
  }
}