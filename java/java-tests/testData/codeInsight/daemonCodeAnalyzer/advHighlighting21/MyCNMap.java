import java.util.*;
import java.util.concurrent.ConcurrentNavigableMap;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

public class MyCNMap<K, V> extends AbstractMap<K, V>
  implements ConcurrentNavigableMap<K, V> {
  public MyCNMap<K, V> clone() {
    throw new InternalError();
  }

  public native boolean containsKey(Object key);

  public native V get(Object key);

  public native V getOrDefault(Object key, V defaultValue);

  public native V put(K key, V value);

  public native V remove(Object key);

  public native boolean containsValue(Object value);

  public native int size();

  public native boolean isEmpty();

  public native void clear();

  public native V computeIfAbsent(K key, Function<? super K, ? extends V> mappingFunction);

  public native V computeIfPresent(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

  public native V compute(K key, BiFunction<? super K, ? super V, ? extends V> remappingFunction);

  public native V merge(K key, V value, BiFunction<? super V, ? super V, ? extends V> remappingFunction);

  public native NavigableSet<K> keySet();

  public native NavigableSet<K> navigableKeySet();

  public native Collection<V> values();

  public native Set<Map.Entry<K, V>> entrySet();

  public native ConcurrentNavigableMap<K, V> descendingMap();

  public native NavigableSet<K> descendingKeySet();

  public native V putIfAbsent(K key, V value);

  public native boolean remove(Object key, Object value);

  public native boolean replace(K key, V oldValue, V newValue);

  public native V replace(K key, V value);

  public native Comparator<? super K> comparator();

  public native K firstKey();

  public native K lastKey();

  public native V putFirst(K k, V v);

  public native V putLast(K k, V v);

  public native ConcurrentNavigableMap<K, V> subMap(K fromKey,
                                                    boolean fromInclusive,
                                                    K toKey,
                                                    boolean toInclusive);

  public native ConcurrentNavigableMap<K, V> headMap(K toKey, boolean inclusive);

  public native ConcurrentNavigableMap<K, V> tailMap(K fromKey, boolean inclusive);

  public native ConcurrentNavigableMap<K, V> subMap(K fromKey, K toKey);

  public native ConcurrentNavigableMap<K, V> headMap(K toKey);

  public native ConcurrentNavigableMap<K, V> tailMap(K fromKey);

  public native Map.Entry<K, V> lowerEntry(K key);

  public native K lowerKey(K key);

  public native Map.Entry<K, V> floorEntry(K key);

  public native K floorKey(K key);

  public native Map.Entry<K, V> ceilingEntry(K key);

  public native K ceilingKey(K key);

  public native Map.Entry<K, V> higherEntry(K key);

  public native K higherKey(K key);

  public native Map.Entry<K, V> firstEntry();

  public native Map.Entry<K, V> lastEntry();

  public native Map.Entry<K, V> pollFirstEntry();

  public native Map.Entry<K, V> pollLastEntry();

  public native void forEach(BiConsumer<? super K, ? super V> action);

  public native void replaceAll(BiFunction<? super K, ? super V, ? extends V> function);
}
