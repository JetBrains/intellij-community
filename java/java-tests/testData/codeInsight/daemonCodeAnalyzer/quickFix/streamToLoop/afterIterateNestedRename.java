// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (int x = 0; x < 20; x++) {
          if (x > 2) {
              long limit = x;
              for (String s = String.valueOf(x); ; s = s + x) {
                  if (limit-- == 0) break;
                  int i = s.length();
                  stat.accept(i);
              }
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}