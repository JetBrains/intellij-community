// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.List;
import java.util.LongSummaryStatistics;

public class Main {
  public static int test(List<String> list) {
      int sum = 0;
      for (String s : list) {
          System.out.println(s);
          int length = s.length();
          sum += length;
      }
      return sum;
  }

  public static LongSummaryStatistics testSummaryStatistics(List<String> list) {
      LongSummaryStatistics stat = new LongSummaryStatistics();
      for (String s : list) {
          System.out.println(s);
          long length = s.length();
          stat.accept(length);
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("aaa", "b", "cc", "dddd")));
    System.out.println(testSummaryStatistics(Arrays.asList("aaa", "b", "cc", "dddd")));
  }
}