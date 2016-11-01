// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.LongSummaryStatistics;

public class Main {
  public static LongSummaryStatistics test(List<String> list) {
      LongSummaryStatistics stat = new LongSummaryStatistics();
      for (String s : list) {
          System.out.println(s);
          long l = s.length();
          stat.accept(l);
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("aaa", "b", "cc", "dddd")));
  }
}