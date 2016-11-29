// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  public static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 500;
      OUTER:
      for (int x = 0; x < 100; x++) {
          long limitInner = x / 2;
          INNER:
          for (int y = 0; y < x; y++) {
              long limit1 = 10;
              int boundInner = y + 100;
              INNER1:
              for (int z = y; z < boundInner; z++) {
                  int bound = z + 2;
                  for (int i = z; i < bound; i++) {
                      if (limit1-- == 0) break INNER1;
                      if (limitInner-- == 0) break INNER;
                      if (limit-- == 0) break OUTER;
                      stat.accept(i);
                  }
              }
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}