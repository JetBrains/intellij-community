import java.util.Map;
import java.util.Set;
import java.util.function.UnaryOperator;

final class Example {
  Map<String, Map<String, Set<String>>> test(Map<String, Map<String, Set<String>>> mapOfMaps) {
    return processMap(
      mapOfMaps,
      mapOfSets -> {
        return processMap(
          mapOfSets,
          set -> {
            if (true) {
              return unionSets(set);
            } else {
              return unionSets(set);
            }
          });
      });
  }

  <T> Set<T> unionSets(Set<T>... sets) {
    return null;
  }

  <K, V> Map<K, V> processMap(Map<K, V> map, UnaryOperator<V> operator) {
    return null;
  }
}