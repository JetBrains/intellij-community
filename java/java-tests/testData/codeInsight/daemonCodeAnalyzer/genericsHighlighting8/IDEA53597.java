import java.util.Comparator;
import java.util.Map;

class C1<K,V> {
  class C2<T> {C2(Comparator<? super T> pComparator) {}}
  C1(Comparator<? super K> pComparator) {new C2<Map.Entry<K,V>>(<error descr="Incompatible types. Found: 'java.util.Comparator<java.util.Map.Entry<capture<? super K>,?>>', required: 'java.util.Comparator<? super java.util.Map.Entry<K,V>>'">m</error>(pComparator));}
  static <T> Comparator<Map.Entry<T,?>> m(Comparator<T> pKeyComparator) {return null;}
}