// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static Map<Integer, String> test(List<String> strings) {
      Map<Integer, String> mapping = new HashMap<>();
      for (String str: strings) {
          if (mapping.put(str.length(), str) != null) {
              throw new IllegalStateException("Duplicate key");
          }
      }
      return mapping;
  }

  public static Map<Integer, String> testMerge(List<String> strings) {
      Map<Integer, String> map = new HashMap<>();
      for (String s: strings) {
          if (!s.isEmpty()) {
              map.merge(s.length(), s, String::concat);
          }
      }
      return map;
  }

  public static TreeMap<Integer, String> testPut(List<String> strings) {
      TreeMap<Integer, String> map = new TreeMap<>();
      for (String s1: strings) {
          if (!s1.isEmpty()) {
              map.put(s1.length(), s1.trim());
          }
      }
      return map;
  }

  public static TreeMap<Integer, String> testSupplier(List<String> strings) {
      TreeMap<Integer, String> map = new TreeMap<>();
      for (String s1: strings) {
          if (!s1.isEmpty()) {
              map.putIfAbsent(s1.length(), s1);
          }
      }
      return map;
  }

  public static void main(String[] args) {
    System.out.println(testMerge(Arrays.asList()));
    System.out.println(testMerge(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testPut(Arrays.asList()));
    System.out.println(testPut(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(testSupplier(Arrays.asList()));
    System.out.println(testSupplier(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}