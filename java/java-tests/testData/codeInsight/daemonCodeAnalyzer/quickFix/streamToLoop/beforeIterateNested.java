// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  public static IntSummaryStatistics test() {
    return IntStream.range(0, 20).flatMap(limit -> Stream.iterate("", x -> x + limit).limit(limit).mapToInt(x -> x.length())).summary<caret>Statistics();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}