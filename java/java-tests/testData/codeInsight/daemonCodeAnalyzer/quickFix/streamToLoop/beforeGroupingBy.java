// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static Map<Integer, List<String>> testSimple(List<String> strings) {
    return strings.stream().co<caret>llect(Collectors.groupingBy(str -> str.length()));
  }

  public static Map<Integer, List<String>> testVarConflict(List<String> strings, int k) {
    return strings.stream().collect(Collectors.groupingBy(String::length));
  }

  static void testCounting(List<String> list) {
    Map<Integer, Long> map = list.stream().collect(Collectors.groupingBy(String::length, Collectors.counting()));
    System.out.println(map);
  }

  private static TreeMap<Integer, LinkedHashSet<String>> testCustomMap(List<String> strings) {
    return strings.stream().collect(
      Collectors.groupingBy(String::length, () -> new TreeMap<>(Comparator.reverseOrder()), Collectors.toCollection(LinkedHashSet::new)));
  }

  static void testSummingDouble(List<String> list) {
    Map<Integer, Double> map4 = list.stream().collect(Collectors.groupingBy(String::length, Collectors.summingDouble(String::length)));
    System.out.println(map4);
  }

  static void testMappingSummingInt(List<String> list) {
    Map<Integer, Integer> map3 = list.stream().collect(Collectors.groupingBy(String::length, Collectors.mapping(String::trim, Collectors.summingInt(String::length))));
    System.out.println(map3);
  }

  public static void testGroupingGroupingToSet(List<String> strings) {
    System.out.println(strings.stream().collect(Collectors.groupingBy(String::length, Collectors.groupingBy(s -> s.charAt(0), Collectors.toSet()))));
  }

  private static TreeMap<Integer, List<Integer>> testMappingToList(List<String> strings) {
    return strings.stream().collect(
      Collectors.groupingBy(String::length, () -> new TreeMap<>(Comparator.reverseOrder()),
                            Collectors.mapping(String::length, Collectors.mapping(len -> len*2, Collectors.toList()))));
  }

  public static void testSummarizingDouble(List<String> strings) {
    System.out.println(strings.stream()
                         .filter(Objects::nonNull)
                         .collect(Collectors.groupingBy(String::length, Collectors.summarizingDouble(String::length))));
  }

  public static void testToMap(List<String> strings) {
    System.out.println(strings.stream().collect(Collectors.groupingBy(String::length, Collectors.toMap(s -> s.charAt(0), Function.identity()))));
  }

  public static void testToSet(List<String> strings) {
    System.out.println(strings.stream().filter(Objects::nonNull).collect(Collectors.groupingBy(String::length, Collectors.toSet())));
  }

  public static void main(String[] args) {
    System.out.println(testSimple(Arrays.asList()));
    System.out.println(testSimple(Arrays.asList("a", "bbb", "cc", "d", "eee")));
    System.out.println(testVarConflict(Arrays.asList(), 1));
    System.out.println(testVarConflict(Arrays.asList("a", "bbb", "cc", "d", "eee"), 2));
    testCounting(Arrays.asList("a", "bbb", "cc", "d", "eee"));
    System.out.println(testCustomMap(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e")));
    testGroupingGroupingToSet(Arrays.asList("a", "bbb", "cccc", "dddd"));
    System.out.println(testMappingToList(Arrays.asList("a", "bbb", "cccc", "dddd", "ee", "e")));
    testSummingDouble(Arrays.asList("a", "bbb", "cccc", "dddd"));
    testMappingSummingInt(Arrays.asList("a", "bbb", "cccc", "dddd"));
    testSummarizingDouble(Arrays.asList("a", "bbb", "cccc", "dddd"));
    testToMap(Arrays.asList("a", "bbb", "cccc", "dddd"));
    testToSet(Arrays.asList("a", "bbb", "cccc", "dddd"));
  }
}