// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (int limit = 0; limit < 20; limit++) {
          long limitInner = limit;
          for (String x = ""; ; x = x + limit) {
              if (limitInner-- == 0) break;
              int length = x.length();
              stat.accept(length);
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}