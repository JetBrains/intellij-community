// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.LongSummaryStatistics;

public class Main {
  public static LongSummaryStatistics test(List<List<String>> list) {
      LongSummaryStatistics stat = new LongSummaryStatistics();
      for (List<String> a : list) {
          if (a != null) {
              for (String s : a) {
                  long length = s.length();
                  stat.accept(length);
              }
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, Arrays.asList("aaa", "b", "cc", "dddd"), Arrays.asList("gggg"))));
  }
}