// "Replace with 'computeIfAbsent' method call" "GENERIC_ERROR_OR_WARNING"
import java.util.*;
class TestGetNullReturn {
  private final Map<String, String> map = new HashMap<>();
  public String getValue(String key) {
    if (map.get(key) == null) {
      map.put(key, "v");
    }
    return map.get(<caret>key);
  }
}

