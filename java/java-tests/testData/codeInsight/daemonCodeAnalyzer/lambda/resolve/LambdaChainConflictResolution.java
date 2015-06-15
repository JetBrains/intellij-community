import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.function.Function;

class MyTest {
  interface I<T, E> {
    List<E> m(Set<T> s);
  }

  <K, L>List<K> foo(I<K, L> i){return null;}

  <K, H> List<K> bar(Runnable l) {return null;}
  List<String> bar(Function<String, String> s) {return null;}

  String baz(Set<Integer> b, String x) {return null;}
  List<Integer> baz(Iterator<Integer> s, Iterator<Integer> i) {return null;}

  {
    List<Integer> l = foo(a -> bar(b -> b<ref>az(a, b)));
  }
}
