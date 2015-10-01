
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Test {

  void foo(Stream<String> pairStream) {
    Map<String, Map<String, Integer>> frequencyMap = pairStream
      .collect(Collectors.toMap(p -> p, p -> of(p, 1), (m1, m2) -> new HashMap<>(m1)));

    Map<String, Map<String, Integer>> frequencyMap1 = pairStream
      .collect(Collectors.toMap(p -> p, p -> of(p, 1), (m1, m2) -> new HashMap<>()));
  }

  public static <K, V> Map<K, V> of(K k1, V v1) {
    return null;
  }
}