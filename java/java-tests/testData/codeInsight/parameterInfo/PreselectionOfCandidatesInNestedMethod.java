
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

class Test {
  void m(Map<String, String> s) {
    s.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, <caret>Map.Entry::getValue));
  }
}