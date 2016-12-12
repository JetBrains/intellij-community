// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public static void test(List<String> strings) {
      final Map<Boolean, Map<Character, Set<String>>> nestedMap =
              new HashMap<>();
      nestedMap.put(false, new HashMap<>());
      nestedMap.put(true, new HashMap<>());
      for (String s : strings) {
          nestedMap.get(s.length() > 2).computeIfAbsent(s.charAt(0), k -> new HashSet<>()).add(s);
      }
      System.out.println(nestedMap);
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "bbb", "cccc", "dddd"));
  }
}
