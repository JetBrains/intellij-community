// "Replace with 'computeIfAbsent' method call" "GENERIC_ERROR_OR_WARNING"
import java.util.Map;
import java.util.Optional;

public class Main {
  public Optional<String> test(Map<String, String> map, String key) {
    if (!map.containsKey(key)) {
      map.put(key, "foo");
    }
    return Optional.of(map.get(<caret>key));
  }
}
