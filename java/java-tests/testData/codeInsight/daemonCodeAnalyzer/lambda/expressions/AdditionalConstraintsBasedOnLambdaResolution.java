import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collector;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  public void testIt()
  {
    Map<Integer, String> innerMap = new HashMap<>();
    innerMap.put(2, "abc");
    Map<Long, Map<Integer, String>> outerMap = new HashMap<>();
    outerMap.put(1L, innerMap);
    Map<Long, Map<Integer, String>> transformedMap = outerMap.entrySet().stream()
      .collect(Collectors.toMap(
        Map.Entry::getKey,
        m -> m.getValue().entrySet().stream()
          .collect(Collectors.toMap(
            Map.Entry::getKey,
            v -> v.getValue().toUpperCase()))));
  }
}