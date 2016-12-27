// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  public List<String> testSorted(List<String> list) {
      List<String> toSort = new ArrayList<>();
      for (String s : list) {
          toSort.add(s);
      }
      toSort.sort(String.CASE_INSENSITIVE_ORDER);
      List<String> result = new ArrayList<>();
      for (String s : toSort) {
          result.add(s);
      }
      return result;
  }
}