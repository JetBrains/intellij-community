// "Extract variable 'set' to 'map' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testMap(List<Map<String, String>> list) {
    list.stream().flatMap(map -> {
      Set<String> <caret>set = map.keySet();
      return set.stream();
    }).forEach(System.out::println);
  }
}