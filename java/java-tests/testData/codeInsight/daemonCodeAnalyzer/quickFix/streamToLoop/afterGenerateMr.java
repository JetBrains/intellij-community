// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static Integer getInt() {
    return 10;
  }

  public static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 33;
      OUTER:
      while (true) {
          Integer x = getInt();
          for (int i = 0; i < x; i++) {
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