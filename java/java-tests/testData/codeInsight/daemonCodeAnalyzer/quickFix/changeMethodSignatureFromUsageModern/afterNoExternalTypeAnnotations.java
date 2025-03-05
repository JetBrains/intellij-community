// "Change 1st parameter of method 'use' from 'Map<String, String>' to 'Map<String, Integer>'" "true" 
import java.util.Map;
import java.util.stream.Collectors;

class Test {
  void testMethodRef(Map<String, Integer> list) {
    var map = list.entrySet().stream()
      .filter(e -> !e.getKey().isEmpty())
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    use(map);
  }

  private void use(Map<String, Integer> collect) {
  }
}