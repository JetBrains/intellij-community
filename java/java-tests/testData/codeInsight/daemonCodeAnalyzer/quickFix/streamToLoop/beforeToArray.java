// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.IntFunction;
import java.util.stream.Stream;

public class Main {
  private static Object[] test(int[] numbers) {
    return Arrays.stream(numbers).boxed().toArr<caret>ay();
  }

  private static Integer[][] test2d(int[] numbers) {
    return Arrays.stream(numbers).boxed().map(n -> new Integer[] {n}).toArray(Integer[][]::new);
  }

  private static Number[] testCovariant(int[] numbers) {
    return Arrays.stream(numbers).boxed().toArray(Integer[]::new);
  }

  private static Number[] testCovariantLambda(int[] numbers) {
    return Arrays.stream(numbers).boxed().toArray(size -> new Integer[size]);
  }

  private static <A> A[] toArraySkippingNulls(List<?> list, IntFunction<A[]> generator) {
    return list.stream().filter(Objects::nonNull).toArray(generator);
  }

  private static List<?>[] testGeneric(int[] numbers) {
    return Arrays.stream(numbers).boxed().map(n -> Collections.singletonList(n)).toArray(List<?>[]::new);
  }

  private static Number[] testTypeMismatch(Object[] objects) {
    return Stream.of(objects).filter(Number.class::isInstance).toArray(Number[]::new);
  }

  public static void main(String[] args) {
    System.out.println(Arrays.asList(test(new int[] {1,2,3})));
    System.out.println(Arrays.asList(test2d(new int[] {1,2,3})));
    System.out.println(Arrays.asList(testCovariant(new int[] {1,2,3})));
    System.out.println(Arrays.asList(testCovariantLambda(new int[] {1,2,3})));
    System.out.println(Arrays.asList(testGeneric(new int[] {1,2,3})));
    System.out.println(Arrays.asList(testTypeMismatch(new Object[]{1, 2, 3, "string", 4})));
  }
}