// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.List;
import java.util.Optional;
import java.util.OptionalInt;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import static java.util.Arrays.asList;

public class Main {
  public static String testReduce2(List<String> list) {
    return list.stream().re<caret>duce("", (a, b) -> a+b);
  }

  public static int testReduce3(List<String> list) {
    return list.stream().reduce(0, (a, b) -> a+b.length(), Integer::sum);
  }

  private static Integer testReduceMethodRef(List<Integer> numbers) {
    return numbers.stream().reduce(0, Math::max);
  }

  private static OptionalInt testReduceOptionalInt() {
    return IntStream.of().reduce((a, b) -> a*b);
  }

  private static Optional<Integer> testReduceOptionalInteger(Integer... numbers) {
    return Stream.of(numbers).reduce((a, b) -> a*b);
  }

  public static void main(String[] args) {
    System.out.println(testReduce2(asList("a", "b", "c")));
    System.out.println(testReduce3(asList("a", "b", "c")));
    testReduceMethodRef(asList(1, 10, 5));
    System.out.println(testReduceOptionalInt());
    System.out.println(testReduceOptionalInteger(1,2,3,4));
  }
}