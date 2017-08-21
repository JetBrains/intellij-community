// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import static java.util.Arrays.asList;
import java.util.*;
import java.util.stream.*;

public class Main {
  public void testAveragingDouble(String... list) {
    System.out.println(Stream.of(list).filter(Objects::nonNull).col<caret>lect(Collectors.averagingDouble(s -> 1.0/s.length())));
  }

  public void testAveragingInt(String... list) {
    System.out.println(Stream.of(list).filter(Objects::nonNull).collect(Collectors.averagingInt(String::length)));
  }

  public static long testCounting(List<String> strings) {
    return strings.stream().filter(s -> !s.isEmpty()).collect(Collectors.counting());
  }

  public static Optional<String> testMaxBy(List<String> strings) {
    return strings.stream().filter(s -> !s.isEmpty()).collect(Collectors.maxBy(Comparator.naturalOrder()));
  }

  public static Optional<String> testReducing1(List<String> list) {
    return list.stream().collect(Collectors.reducing((a, b) -> a+b));
  }

  public static String testReducing2(List<String> list) {
    return list.stream().collect(Collectors.reducing("", (a, b) -> a+b));
  }

  static Integer testReducing3() {
    Integer totalLength = Stream.of("a", "bb", "ccc").collect(Collectors.reducing(0, String::length, Integer::sum));
    return totalLength;
  }

  public static DoubleSummaryStatistics testSummarizingDouble(List<String> strings) {
    return strings.stream().filter(Objects::nonNull).collect(Collectors.summarizingDouble(str -> str.length()/2.0));
  }

  public static Double testSummingDouble(List<String> strings) {
    return strings.stream().filter(Objects::nonNull).collect(Collectors.summingDouble(String::length));
  }

  private static TreeSet<Integer> testToCollection() {
    return IntStream.of(4, 2, 1).boxed().collect(Collectors.toCollection(TreeSet::new));
  }

  // Unresolved reference
  void f(Collection<? extends Foo> c) {
    Set<Foo> uniqueDescriptors = c.stream()
      .collect(Collectors.toCollection(() -> new TreeSet()));
  }

  public static void main(String[] args) {
    new Main().testAveragingDouble("a", "bbb", null, "cc", "dd", "eedasfasdfs");
    new Main().testAveragingInt("a", "bbb", null, "cc", "dd", "eedasfasdfs");
    System.out.println(testCounting(Arrays.asList()));
    System.out.println(testCounting(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testMaxBy(Arrays.asList()));
    System.out.println(testMaxBy(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testReducing1(asList("a", "b", "c")));
    System.out.println(testReducing2(asList("a", "b", "c")));
    System.out.println(testReducing3());
    System.out.println(testSummarizingDouble(Arrays.asList(null, null)));
    System.out.println(testSummarizingDouble(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testSummingDouble(Arrays.asList(null, null)));
    System.out.println(testSummingDouble(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testToCollection());
  }
}
