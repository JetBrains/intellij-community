// "Replace with 'computeIfAbsent' method call" "GENERIC_ERROR_OR_WARNING"
import java.util.*;
class TestWithComments {
  Map<String, List<String>> targetMap = new HashMap<>();
  public List<String> getWrapperOriginal(String key, List<String> v) {
    // pre-if comment
    if(!targetMap.containsKey(key)) { // inline if comment
      // before put
      targetMap.put(key, v); // inline put
      // after put
    }
    // between if and return
    return targetMap.get(<caret>key);
  }
}

