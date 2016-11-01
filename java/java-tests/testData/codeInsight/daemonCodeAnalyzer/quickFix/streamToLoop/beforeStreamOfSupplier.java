// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Main {
  private static IntSummaryStatistics test() {
    return Stream.<Supplier<Integer>>of(() -> 1, () -> 2, () -> 3).mapToInt(sup -> sup.get()).summar<caret>yStatistics();
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}