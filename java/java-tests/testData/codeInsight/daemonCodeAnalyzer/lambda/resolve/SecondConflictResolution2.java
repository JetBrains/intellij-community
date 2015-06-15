
import java.util.Iterator;
import java.util.List;
import java.util.Set;

class MyTest {
  interface I<T, E> {
    List<E> m(Set<T> s);
  }
  <K, L>List<K> foo(I<K, L> i){return null;}

  <K> List<K> bar(List<K> l) {return l;}
  List<String> bar(String s) {return null;}

  String baz(String b, String x) {return b;}
  List<Integer> baz(Iterator<Integer> s, Iterator<Integer> i) {return null;}

  {
    List<Integer> l = foo(a -> bar(ba<ref>z(a.iterator(), a.iterator())));
  }
}
