// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.Stream;

public class Main {
  private static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (Integer i : new Integer[]{1, 2, 3}) {
          int i1 = i;
          stat.accept(i1);
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}