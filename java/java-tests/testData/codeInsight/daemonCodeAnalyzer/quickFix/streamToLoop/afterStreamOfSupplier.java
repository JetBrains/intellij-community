// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Main {
  private static IntSummaryStatistics test() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (Supplier<Integer> sup : Arrays.<Supplier<Integer>>asList(() -> 1, () -> 2, () -> 3)) {
          int i = sup.get();
          stat.accept(i);
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}