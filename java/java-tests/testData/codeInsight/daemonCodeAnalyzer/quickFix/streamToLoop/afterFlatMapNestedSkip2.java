// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  public static IntSummaryStatistics test(int... values) {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long toSkip = 1;
      for (int x : values) {
          if (x > 0) {
              long toSkipInner = x;
              for (int i = 0; i < 100; i++) {
                  if (toSkipInner > 0) {
                      toSkipInner--;
                      continue;
                  }
                  if (toSkip > 0) {
                      toSkip--;
                      continue;
                  }
                  stat.accept(i);
              }
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test(1, 95, -2, 0, 97, 90));
  }
}