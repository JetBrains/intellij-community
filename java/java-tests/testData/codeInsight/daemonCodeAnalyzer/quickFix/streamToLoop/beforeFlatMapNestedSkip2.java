// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;

public class Main {
  public static IntSummaryStatistics test(int... values) {
    return Arrays.stream(values).filter(x -> x > 0).flatMap(v -> IntStream.range(0, 100).skip(v)).skip(1).summarySta<caret>tistics();
  }

  public static void main(String[] args) {
    System.out.println(test(1, 95, -2, 0, 97, 90));
  }
}