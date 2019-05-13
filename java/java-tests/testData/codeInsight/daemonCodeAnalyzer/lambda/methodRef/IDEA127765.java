import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

class IDEA127765 {
  void a(final Map<String, Optional<Double>> allValues, final Function<Optional<Double>, Double> get) {
    final Map<String, Double> presentValues = transformValues(filterValues(allValues, Optional::isPresent), get);
  }

  public static <K, V1, V2> Map<K, V2> transformValues(Map<K, V1> fromMap, Function<? super V1, V2> function) {
    return null;
  }

  public static <K, V> Map<K, V> filterValues(Map<K, V> unfiltered, Predicate<? super V> valuePredicate) {
    return null;
  }

}
