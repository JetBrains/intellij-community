
import java.util.*;
import java.util.function.*;

class Test {
  <E, K> HashMap<K, ArrayList<E>> foo(ArrayList<E> list, BiFunction<E, Comparable<E>, K> getKey) {
    return null;
  }

  void bar(ArrayList<? extends Number> list) {
    HashMap<Integer, ? extends ArrayList<? extends Number>> foo = foo(list, (x, c) -> c.compareTo(x));
  }

  Set<Integer> baz(ArrayList<? extends Number> list) {
    return foo(list, (x, c) -> c.compareTo(x)).keySet(); // False error in lambda: 'compareTo(capture<? extends java.lang.Number>)' in 'java.lang.Comparable' cannot be applied to '(java.lang.Number)'
  }
}
