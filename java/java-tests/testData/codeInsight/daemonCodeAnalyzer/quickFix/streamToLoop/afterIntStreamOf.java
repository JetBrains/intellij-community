// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  private static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (int i : new int[]{1,/*two*/2,/*three*/3}) {
          stat.accept(i);
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}