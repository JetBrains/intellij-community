// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;

public class Main {
  public static Map<Boolean, List<String>> test(List<String> strings) {
      Map<Boolean, List<String>> map = new HashMap<>();
      map.put(false, new ArrayList<>());
      map.put(true, new ArrayList<>());
      for (String s : strings) {
          if (!s.isEmpty()) {
              map.get(s.length() > 1).add(s);
          }
      }
      return map;
  }

  static void testCounting(List<String> list) {
      Map<Boolean, Long> map2 = new HashMap<>();
      map2.put(false, 0L);
      map2.put(true, 0L);
      for (String s : list) {
          map2.merge(s.isEmpty(), 1L, Long::sum);
      }
      System.out.println(map2);
  }

  static void testSummingDouble(List<String> list) {
      Map<Boolean, Double> map1 = new HashMap<>();
      map1.put(false, 0.0);
      map1.put(true, 0.0);
      for (String s : list) {
          map1.merge(s.isEmpty(), (double) s.length(), Double::sum);
      }
      System.out.println(map1);
  }

  public static void testGroupingToSet(List<String> strings) {
      final Map<Boolean, Map<Character, Set<String>>> nestedMap =
              new HashMap<>();
      nestedMap.put(false, new HashMap<>());
      nestedMap.put(true, new HashMap<>());
      for (String s : strings) {
          nestedMap.get(s.length() > 2).computeIfAbsent(s.charAt(0), k -> new HashSet<>()).add(s);
      }
      System.out.println(nestedMap);
  }

  public static void testToCollection(List<String> strings) {
      Map<Boolean, LinkedHashSet<String>> map = new HashMap<>();
      map.put(false, new LinkedHashSet<>());
      map.put(true, new LinkedHashSet<>());
      for (String s : strings) {
          map.get(s.length() > 2).add(s);
      }
      System.out.println(map);
  }

  public static void testToMapNameConflict(List<String> strings) {
      // and collect
      Map<Boolean, Map<String, Integer>> map = new HashMap<>();
      map.put(false, new HashMap<>());
      map.put(true, new HashMap<>());
      for (String string : strings) {
          String s = string/*trimming*/.trim();
          if (map.get(s.length() /*too big!*/ > 2).put(((UnaryOperator<String>) /* cast is necessary here */ x -> x).apply(s), s.length()) != null) {
              throw new IllegalStateException("Duplicate key");
          }
      }
      System.out.println(map);
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