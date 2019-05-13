
import java.util.*;
import java.util.function.Function;

class Test {
  public static <V, K> List<V> map(Collection<? extends K> iterable, Function<K, V> f) {
    return null;
  }

  static class Pair<A, B> {
    public Pair(A a, B b) {
    }
  }


  void f(Set c){
    List<Pair<String, Integer>> l = map(c, s -> new Pair(s, null));
  }
}