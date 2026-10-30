// "Replace with 'computeIfAbsent' method call" "GENERIC_ERROR_OR_WARNING"
import java.util.*;
class TestWithComments {
  Map<String, List<String>> targetMap = new HashMap<>();
  public List<String> getWrapperOriginal(String key, List<String> v) {
    // pre-if comment
      // inline if comment
      // before put
      // inline put
      // after put
      // between if and return
      return targetMap.computeIfAbsent(key, k -> v);
  }
}

