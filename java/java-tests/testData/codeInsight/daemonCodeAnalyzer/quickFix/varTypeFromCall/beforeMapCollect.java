// "Change variable 'map' type to 'Map<String, Integer>'" "true-preview"
import java.util.Map;
import java.util.stream.Collectors;

class Test {
  void testMethodRef(Map<String, Integer> list) {
    Map<String, String> map = list.entrySet().stream()
      .filter(e -> !e.getKey().isEmpty())
      .<caret>collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
  }
}