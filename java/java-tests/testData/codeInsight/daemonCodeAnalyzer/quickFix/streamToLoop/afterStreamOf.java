// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.IntSummaryStatistics;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  private static IntSummaryStatistics testOfArray() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (Integer i: new Integer[] /*create array*/{1, 2, 3}) {
          int i1 = i;
          stat.accept(i1);
      }
      return stat;
  }

  private static IntSummaryStatistics testOfArrayAsElement() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (Number[] nums: Arrays.<Number[]>asList(new Integer[]{1, 2, 3})) {
          int num = (int) nums[0];
          stat.accept(num);
      }
      return stat;
  }

  private static IntSummaryStatistics testOfSupplier() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (Supplier<Integer> sup: Arrays.<Supplier<Integer>>asList(() -> 1, /*between*/ () /*supply 2*/ -> 2, () -> 3)) {
          int i = sup.get();
          stat.accept(i);
      }
      return stat;
  }

  private static IntSummaryStatistics testIntStreamOf() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (int i: new int[]{1,/*two*/2,/*three*/3}) {
          stat.accept(i);
      }
      return stat;
  }

  private static IntSummaryStatistics testIntStreamOfArray() {
      IntSummaryStatistics stat = new IntSummaryStatistics();
      for (int i: new int[]{1, 2, 3}) {
          stat.accept(i);
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(testOfArray());
    System.out.println(testOfArrayAsElement());
    System.out.println(testOfSupplier());
    System.out.println(testIntStreamOf());
    System.out.println(testIntStreamOfArray());
  }
}