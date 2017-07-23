// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.IntSummaryStatistics;
import java.util.function.Supplier;
import java.util.stream.IntStream;
import java.util.stream.Stream;

public class Main {
  private static IntSummaryStatistics testOfArray() {
    return Stream.of(new Integer[] /*create array*/{1,2,3}).mapToInt(i -> i).summaryStat<caret>istics();
  }

  private static IntSummaryStatistics testOfArrayAsElement() {
    return Stream.<Number[]>of(new Integer[] {1,2,3}).mapToInt(nums -> (int)nums[0]).summaryStatistics();
  }

  private static IntSummaryStatistics testOfSupplier() {
    return Stream.<Supplier<Integer>>of(() -> 1, /*between*/ () /*supply 2*/ -> 2, () -> 3).mapToInt(sup -> sup.get()).summaryStatistics();
  }

  private static IntSummaryStatistics testIntStreamOf() {
    return IntStream.of(1,/*two*/2,/*three*/3).summaryStatistics();
  }

  private static IntSummaryStatistics testIntStreamOfArray() {
    return IntStream.of(new int[] {1,2,3}).summaryStatistics();
  }

  public static void main(String[] args) {
    System.out.println(testOfArray());
    System.out.println(testOfArrayAsElement());
    System.out.println(testOfSupplier());
    System.out.println(testIntStreamOf());
    System.out.println(testIntStreamOfArray());
  }
}