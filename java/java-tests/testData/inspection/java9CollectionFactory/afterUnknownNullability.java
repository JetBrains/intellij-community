// "Fix all 'Immutable collection creation can be replaced with collection factory call' problems in file" "true"
import java.util.*;

class Main {
  void nothingInterestingHere() {
    System.out.println(of(null, null));
  }

  public <K, V> Map<K, V> of(K k1, V v1) {
    Objects.requireNonNull(k1);
    Objects.requireNonNull(v1);
      return Map.of(k1, v1);
  }

  public <K, V> Map<K, V> of1(K k1, V v1) {
    Map<K, V> map = new HashMap<>(1);
    map.put(k1, v1);
    return Collections.unmodifiableMap(map);
  }
}