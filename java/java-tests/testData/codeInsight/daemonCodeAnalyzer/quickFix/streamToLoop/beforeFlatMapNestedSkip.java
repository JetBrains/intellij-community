// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  public static IntSummaryStatistics test(int... values) {
    return Arrays.stream(values).skip(1).filter(x -> x > 0).flatMap(v -> IntStream.range(0, 100).skip(v)).summaryStatisti<caret>cs();
  }

  public static void main(String[] args) {
    System.out.println(test(1, 95, -2, 0, 97, 90));
  }
}