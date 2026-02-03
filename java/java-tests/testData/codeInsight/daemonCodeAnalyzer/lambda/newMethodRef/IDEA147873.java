import java.util.*;

class Test {

  public static LinkedMultiValueMap<String, String> main(Map<String, String> queryParams) {
      return queryParams.entrySet().stream()
      .collect(LinkedMultiValueMap<String, String>::new, (m, e) -> m.add(e.getKey(), e.getValue()),
               LinkedMultiValueMap::putAll);
  }
}
class LinkedMultiValueMap<K, V> extends HashMap<K, List<V>> {
  public LinkedMultiValueMap() {}
  public LinkedMultiValueMap(int initialCapacity) {}
  public LinkedMultiValueMap(Map<K, List<V>> otherMap) {}

  public void add(K key, V value) {}
  public void putAll(Map<? extends K, ? extends List<V>> m) {}
}
