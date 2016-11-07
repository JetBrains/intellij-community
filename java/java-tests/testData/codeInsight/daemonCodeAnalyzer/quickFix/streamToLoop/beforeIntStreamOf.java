// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  private static IntSummaryStatistics test() {
    return IntStream.of(1,2,3).summaryStatist<caret>ics();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}