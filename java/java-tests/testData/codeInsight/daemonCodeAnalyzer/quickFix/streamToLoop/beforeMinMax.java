// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.IntSupplier;

public class Main {
  public static String testMaxComparator(List<String> strings) {
    return strings.stream().ma<caret>x(Comparator.comparing(String::length)).orElse(null);
  }

  public static OptionalDouble testMaxDouble(List<String> strings) {
    return strings.stream().mapToDouble(String::length).max();
  }

  private static Optional<String> testMaxLambda(Map<String, List<String>> dependencies, String fruits, Map<String, Integer> weights) {
    return dependencies.get(fruits).stream().max((o1, o2) -> weights.get(o1)-weights.get(o2));
  }

  private static Optional<String> testMaxLambdaTernary(Map<String, List<String>> dependencies, String fruits, Map<String, String> weights) {
    return dependencies.get(fruits).stream().max((o1, o2) -> o1.compareTo(o2) < 0 ? -1 : o1.compareTo(o2) > 0 ? 1 : 0);
  }

  private static Optional<String> testMaxReverseOrder(Map<String, List<String>> dependencies, String fruits, Map<String, String> weights) {
    return dependencies.get(fruits).stream().max(Collections.reverseOrder());
  }

  public static String testMinPassedComparator(List<String> strings, Comparator<String> cmp) {
    return strings.stream().min(cmp).orElse(null);
  }

  public static String testMinReversedComparator(List<String> strings, Comparator<CharSequence> comparator) {
    return strings.stream().min(comparator.reversed()).orElseGet(strings::toString);
  }

  public static int testMinInt(List<String> strings, IntSupplier supplier) {
    return strings.stream().mapToInt(String::length).min().orElseGet(supplier);
  }

  public static int testMinMaxValue(List<String> strings) {
    return strings.stream().mapToInt(String::length).min().orElse(Integer.MAX_VALUE);
  }

  public static long testMaxMinValue(List<String> strings) {
    long max = strings.stream().mapToLong(String::length).max().orElse(Long.MIN_VALUE);
    return max;
  }

  public static void main(String[] args) {
    System.out.println(testMaxComparator(Arrays.asList()));
    System.out.println(testMaxComparator(Arrays.asList("a", "bbb", "cc", "d", "eee")));
    System.out.println(testMaxDouble(Arrays.asList()));
    System.out.println(testMaxDouble(Arrays.asList("a", "bbb", "cc", "d")));
    System.out.println(testMinPassedComparator(Arrays.asList(), Comparator.comparing(String::length)));
    System.out.println(testMinPassedComparator(Arrays.asList("a", "bbb", "cc", "d", "eee"), Comparator.comparing(String::length)));
    System.out.println(testMinReversedComparator(Arrays.asList(), Comparator.comparing(CharSequence::length)));
    System.out.println(testMinReversedComparator(Arrays.asList("a", "bbb", "cc", "d", "eee"), Comparator.comparing(CharSequence::length)));
    System.out.println(testMinInt(Arrays.asList(), () -> -1));
    System.out.println(testMinInt(Arrays.asList("a", "bbb", "cc", "d"), () -> 2));
  }
}