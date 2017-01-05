// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  public String testSorted(List<List<String>> list) {
      for (List<String> lst : list) {
          List<String> toSort = new ArrayList<>();
          for (String x : lst) {
              if (x != null) {
                  toSort.add(x);
              }
          }
          toSort.sort(null);
          for (String x : toSort) {
              if (x.length() < 5) {
                  return x;
              }
          }
      }
      return "";
  }
}