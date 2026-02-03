// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static Map<Integer, String> test(List<String> strings) {
    Map<Integer, String> mapping = strings.stream()
      .co<caret>llect(Collectors.toMap(String::length, str -> str));
    return mapping;
  }

  public static Map<Integer, String> testMerge(List<String> strings) {
    return strings.stream().filter(s -> !s.isEmpty())
      .collect(Collectors.toMap(String::length, Function.identity(), String::concat));
  }

  public static TreeMap<Integer, String> testPut(List<String> strings) {
    return strings.stream().filter(s -> !s.isEmpty())
      .collect(Collectors.toMap(String::length, String::trim, (s, string) -> string, TreeMap::new));
  }

  public static TreeMap<Integer, String> testSupplier(List<String> strings) {
    return strings.stream().filter(s -> !s.isEmpty())
      .collect(Collectors.toMap(String::length, s -> s, (s, string) -> s, TreeMap::new));
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