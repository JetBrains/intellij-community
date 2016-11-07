// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.stream.Stream;

public class Main {
  public static IntSummaryStatistics test() {
    return Stream.iterate("", x -> x + "a").limit(20).mapToInt(x -> x.length()).summaryStatisti<caret>cs();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}