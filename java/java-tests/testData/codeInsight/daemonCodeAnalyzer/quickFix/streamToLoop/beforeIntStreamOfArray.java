// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  private static IntSummaryStatistics test() {
    return IntStream.of(new int[] {1,2,3}).summary<caret>Statistics();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}