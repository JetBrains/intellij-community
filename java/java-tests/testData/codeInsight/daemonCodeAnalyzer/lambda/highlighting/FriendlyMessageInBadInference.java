import java.util.*;
import java.util.Map.Entry;
import java.util.stream.Collectors;

// IDEA-362351
public class FriendlyMessageInBadInference {
  void test(Map<Integer, Integer> someMap) {
    use(someMap.entrySet().stream()
      .sorted(Comparator.comparingInt(Entry::getKey))
      .<error descr="Incompatible types. Found: 'java.util.HashMap<java.lang.Integer,java.lang.Integer>', required: 'java.lang.Throwable'">collect</error>(Collectors.toMap(Entry::getKey, Entry::getValue, (k1, k2) -> k1, HashMap::new)));
  }
  
  void use(Throwable t) {}
}