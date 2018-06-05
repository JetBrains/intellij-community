// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static Map<Integer, List<String>> testSimple(List<String> strings) {
      Map<Integer, List<String>> map = new HashMap<>();
      for (String str: strings) {
          map.computeIfAbsent(str.length(), k -> new ArrayList<>()).add(str);
      }
      return map;
  }

  public static Map<Integer, List<String>> testVarConflict(List<String> strings, int k) {
      Map<Integer, List<String>> map = new HashMap<>();
      for (String string: strings) {
          map.computeIfAbsent(string.length(), key -> new ArrayList<>()).add(string);
      }
      return map;
  }

  static void testCounting(List<String> list) {
      Map<Integer, Long> map = new HashMap<>();
      for (String s: list) {
          map.merge(s.length(), 1L, Long::sum);
      }
      System.out.println(map);
  }

  private static TreeMap<Integer, LinkedHashSet<String>> testCustomMap(List<String> strings) {
      TreeMap<Integer, LinkedHashSet<String>> map = new TreeMap<>(Comparator.reverseOrder());
      for (String string: strings) {
          map.computeIfAbsent(string.length(), k -> new LinkedHashSet<>()).add(string);
      }
      return map;
  }

  static void testSummingDouble(List<String> list) {
      Map<Integer, Double> map4 = new HashMap<>();
      for (String s: list) {
          map4.merge(s.length(), (double) s.length(), Double::sum);
      }
      System.out.println(map4);
  }

  static void testMappingSummingInt(List<String> list) {
      Map<Integer, Integer> map3 = new HashMap<>();
      for (String s: list) {
          String trim = s.trim();
          map3.merge(s.length(), trim.length(), Integer::sum);
      }
      System.out.println(map3);
  }

  public static void testGroupingGroupingToSet(List<String> strings) {
      Map<Integer, Map<Character, Set<String>>> map = new HashMap<>();
      for (String s: strings) {
          map.computeIfAbsent(s.length(), key -> new HashMap<>()).computeIfAbsent(s.charAt(0), k -> new HashSet<>()).add(s);
      }
      System.out.println(map);
  }

  private static TreeMap<Integer, List<Integer>> testMappingToList(List<String> strings) {
      TreeMap<Integer, List<Integer>> map = new TreeMap<>(Comparator.reverseOrder());
      for (String string: strings) {
          Integer len = string.length();
          Integer integer = len * 2;
          map.computeIfAbsent(string.length(), k -> new ArrayList<>()).add(integer);
      }
      return map;
  }

  public static void testSummarizingDouble(List<String> strings) {
      Map<Integer, DoubleSummaryStatistics> map = new HashMap<>();
      for (String string: strings) {
          if (string != null) {
              map.computeIfAbsent(string.length(), k -> new DoubleSummaryStatistics()).accept(string.length());
          }
      }
      System.out.println(map);
  }

  public static void testToMap(List<String> strings) {
      Map<Integer, Map<Character, String>> map = new HashMap<>();
      for (String s: strings) {
          if (map.computeIfAbsent(s.length(), k -> new HashMap<>()).put(s.charAt(0), s) != null) {
              throw new IllegalStateException("Duplicate key");
          }
      }
      System.out.println(map);
  }

  public static void testToSet(List<String> strings) {
      Map<Integer, Set<String>> map = new HashMap<>();
      for (String string: strings) {
          if (string != null) {
              map.computeIfAbsent(string.length(), k -> new HashSet<>()).add(string);
          }
      }
      System.out.println(map);
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