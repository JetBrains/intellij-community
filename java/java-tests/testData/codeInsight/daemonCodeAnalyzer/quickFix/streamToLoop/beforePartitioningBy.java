// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class Main {
  public static Map<Boolean, List<String>> test(List<String> strings) {
    return strings.stream().filter(s -> !s.isEmpty()).co<caret>llect(Collectors.partitioningBy(s -> s.length() > 1));
  }

  static void testCounting(List<String> list) {
    Map<Boolean, Long> map2 = list.stream()
      .collect(Collectors.partitioningBy(String::isEmpty, Collectors.counting()));
    System.out.println(map2);
  }

  static void testSummingDouble(List<String> list) {
    Map<Boolean, Double> map1 = list.stream()
      .collect(Collectors.partitioningBy(String::isEmpty, Collectors.summingDouble(String::length)));
    System.out.println(map1);
  }

  public static void testGroupingToSet(List<String> strings) {
    final Map<Boolean, Map<Character, Set<String>>> nestedMap =
      strings.stream().collect(Collectors.partitioningBy((String s) -> s.length() > 2, Collectors.groupingBy(s -> s.charAt(0), Collectors.toSet())));
    System.out.println(nestedMap);
  }

  public static void testToCollection(List<String> strings) {
    System.out.println(strings.stream().collect(Collectors.partitioningBy(s -> s.length() > 2, Collectors.toCollection(LinkedHashSet::new))));
  }

  public static void testToMapNameConflict(List<String> strings) {
    System.out.println(strings.stream().map(x -> x/*trimming*/.trim()) // and collect
                         .collect(Collectors.partitioningBy(s -> s.length() /*too big!*/ > 2,
                                                            Collectors.toMap(s -> ((UnaryOperator<String>) /* cast is necessary here */ x -> x).apply(s),
                                                                             String::length))));
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    testCounting(Arrays.asList("a", "", "bbb", "c", "a", ""));
    testSummingDouble(Arrays.asList("a", "", "bbb", "c", "a", ""));
    testGroupingToSet(Arrays.asList("a", "bbb", "cccc", "dddd"));
    testToCollection(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e", "e"));
    testToMapNameConflict(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e", "e"));
  }
}