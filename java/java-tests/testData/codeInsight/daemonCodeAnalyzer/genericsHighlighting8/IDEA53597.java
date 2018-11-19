import java.util.Comparator;
import java.util.Map;

class C1<K,V> {
  class C2<T> {C2(Comparator<? super T> pComparator) {}}
  C1(Comparator<? super K> pComparator) {new C2<Map.Entry<K,V>>(<error descr="Incompatible types. Required Comparator<? super Entry<K, V>> but 'm' was inferred to Comparator<Entry<T, ?>>:
Incompatible equality constraint: K and capture of ? super K">m(pComparator)</error>);}
  static <T> Comparator<Map.Entry<T,?>> m(Comparator<T> pKeyComparator) {return null;}
}