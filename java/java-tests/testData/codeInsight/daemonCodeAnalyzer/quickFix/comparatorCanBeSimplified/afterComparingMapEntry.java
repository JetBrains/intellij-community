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
      .sorted(Entry.comparingByValue(Comparator.reverseOrder()))
      .sorted(Entry.comparingByValue())
      .sorted(Entry.comparingByKey())
      .sorted(Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (oldValue, newValue) -> oldValue, LinkedHashMap::new));

    System.out.println("Sorted...");
    System.out.println(result);
  }
}