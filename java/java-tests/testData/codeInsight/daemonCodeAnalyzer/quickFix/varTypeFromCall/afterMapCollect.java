// "Change variable 'map' type to 'Map<String, Integer>'" "true-preview"
import java.util.Map;
import java.util.stream.Collectors;

class Test {
  void testMethodRef(Map<String, Integer> list) {
    Map<String, Integer> map = list.entrySet().stream()
      .filter(e -> !e.getKey().isEmpty())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}