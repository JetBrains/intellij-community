// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  public static IntSummaryStatistics test() {
    return IntStream.range(0, 100).flatMap(x -> IntStream.range(0, x).flatMap(y -> IntStream.range(y, y + 100).limit(10)).limit(x / 2)).limit(500).summaryStat<caret>istics();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}