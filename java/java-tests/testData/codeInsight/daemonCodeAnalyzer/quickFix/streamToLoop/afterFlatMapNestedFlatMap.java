// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.List;

import static java.util.Arrays.asList;

public class Main {
  public static IntSummaryStatistics test(List<List<List<String>>> list) {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (List<List<String>> l : list) {
          if (l != null) {
              for (List<String> lst : l) {
                  if (lst != null) {
                      for (String str : lst) {
                          int i = str.length();
                          stat.accept(i);
                      }
                  }
              }
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(asList("a", "bbb", "ccc")), asList(), null, asList(asList("z")))));
  }
}