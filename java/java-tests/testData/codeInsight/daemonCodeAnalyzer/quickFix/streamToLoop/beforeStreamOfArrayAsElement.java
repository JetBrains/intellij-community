// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.Stream;

public class Main {
  private static IntSummaryStatistics test() {
    return Stream.<Number[]>of(new Integer[] {1,2,3}).mapToInt(nums -> (int)nums[0]).summaryStatisti<caret>cs();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}