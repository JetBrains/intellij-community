import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

// IDEA-362351
public class FriendlyMessageInBadInference {
  void test(Map<Integer, Integer> someMap) {
    use(<error descr="Incompatible types. Found: 'java.util.HashMap<java.lang.Integer,java.lang.Integer>', required: 'java.lang.Throwable'">someMap.entrySet().stream()
      .sorted(Comparator.comparingInt(Entry::getKey))
      .collect(Collectors.toMap(Entry::getKey, Entry::getValue, (k1, k2) -> k1, HashMap::new))</error>);
  }
  
  void use(Throwable t) {}
}