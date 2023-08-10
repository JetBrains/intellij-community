// "Replace Stream API chain with loop" "true-preview"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  public static IntSummaryStatistics test() {
    return IntStream.range(0, 100).limit(50).summaryStatis<caret>tics();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}