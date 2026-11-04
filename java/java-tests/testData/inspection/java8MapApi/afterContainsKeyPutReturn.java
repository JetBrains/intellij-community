// "Replace with 'computeIfAbsent' method call" "GENERIC_ERROR_OR_WARNING"
import java.util.*;
class Test {
  Map<String, List<String>> targetMap = new HashMap<>();
  public List<String> getWrapperOriginal(String key, List<String> v) {
      return targetMap.computeIfAbsent(key, k -> v);
  }
}
