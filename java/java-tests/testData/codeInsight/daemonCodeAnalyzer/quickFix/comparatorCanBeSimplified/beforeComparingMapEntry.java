// "Fix all 'Comparator method can be simplified' problems in file" "true"
import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

class Test {
  void test() {
    Map<String, Integer> unsortMap = new HashMap<>();
    unsortMap.put("z", 10);
    unsortMap.put("b", 5);
    unsortMap.put("a", 6);
    unsortMap.put("c", 20);
    unsortMap.put("d", 1);

    Map<String, Integer> result = unsortMap.entrySet().stream()
      .sorted(Comparator.<caret>comparing(Map.Entry::getValue, Comparator.reverseOrder()) )
      .sorted(Comparator.comparing(Map.Entry::getValue) )
      .sorted(Comparator.comparing(Map.Entry::getKey) )
      .sorted(Comparator.comparing(Entry::getKey, String.CASE_INSENSITIVE_ORDER) )
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (oldValue, newValue) -> oldValue, LinkedHashMap::new));

    System.out.println("Sorted...");
    System.out.println(result);
  }
}