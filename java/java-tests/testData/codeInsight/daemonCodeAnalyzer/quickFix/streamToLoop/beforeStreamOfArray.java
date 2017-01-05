// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.Stream;

public class Main {
  private static IntSummaryStatistics test() {
    return Stream.of(new Integer[] /*create array*/{1,2,3}).mapToInt(i -> i).summaryStatisti<caret>cs();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}