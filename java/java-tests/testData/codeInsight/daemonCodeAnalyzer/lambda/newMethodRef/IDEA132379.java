import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

class Java8Test {
  public void testCollectorWithProvider() {
    Map<String, Set<String>> map1 = new HashMap<>();
    map1.put("key", new HashSet<>(Arrays.asList("value", "anotherValue")));

    Map<String, Set<Integer>> map2 =
      map1.entrySet().stream()
        .collect(Collectors.toMap(
                   Map.Entry::getKey,
                   entry -> entry.getValue().stream().map(String::length).collect(Collectors.toSet()),
                   (s1, s2) -> {
                     if (s1.equals(s2)) {
                       throw new IllegalArgumentException("duplicate not allowed");
                     }
                     return null;
                   },
                   ConcurrentHashMap::new
                 )
        );

    System.out.println("map1: " + map1);
    System.out.println("map2: " + map2);
  }
}