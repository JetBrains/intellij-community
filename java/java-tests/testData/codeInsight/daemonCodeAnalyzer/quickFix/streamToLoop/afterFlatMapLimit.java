// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  public static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 50;
      OUTER:
      for (int x = 0; x < 100; x++) {
          long limitInner = x / 2;
          for (int i = 0; i < x; i++) {
              if (limitInner-- == 0) break;
              if (limit-- == 0) break OUTER;
              stat.accept(i);
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}