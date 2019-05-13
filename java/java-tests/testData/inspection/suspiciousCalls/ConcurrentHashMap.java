import java.util.concurrent.ConcurrentHashMap;

class Main<K, V> {
  void f(ConcurrentHashMap<K, V> map, K key){
    if (map.contains(<warning descr="Suspicious call to 'ConcurrentHashMap.contains'">key</warning>)) {
      System.out.println();
    }
  }
}
