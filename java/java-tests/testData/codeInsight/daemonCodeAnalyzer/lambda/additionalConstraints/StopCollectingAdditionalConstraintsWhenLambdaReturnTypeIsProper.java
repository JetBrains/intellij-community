import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {
  private List<Map<String, String>> foo(Stream<Map.Entry<String, List<String>>> stream) {
    return then(v -> stream.collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().toString())));
  }

  private <V> List<V> then(Function<Void, V> f) {
    return null;
  }
}
