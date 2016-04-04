
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

class Test {
  static <T> Map<T, Integer> combine(Collection<Map<? extends T, Integer>> pMaps) {
    Function<Map<? extends T, Integer>, Set<? extends Map.Entry<? extends T, Integer>>> entrySet = Map::entrySet;
    return pMaps.stream()
      .map(entrySet)
      .flatMap(Collection::stream)
      .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
                                (pEntry1, pEntry2) -> pEntry1 + pEntry2,
                                (Supplier<HashMap<T, Integer>>) HashMap::new
               )
      );
  }
}