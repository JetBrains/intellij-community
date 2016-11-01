// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.stream.Stream;

public class Main {
  private static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (Number[] nums : Arrays.<Number[]>asList(new Integer[]{1, 2, 3})) {
          int i = (int) nums[0];
          stat.accept(i);
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}