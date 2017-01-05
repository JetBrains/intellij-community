// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.Stream;

public class Main {
  public static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      long limit = 20;
      for (String x = ""; ; x = x + "a") {
          if (limit-- == 0) break;
          int length = x.length();
          stat.accept(length);
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}