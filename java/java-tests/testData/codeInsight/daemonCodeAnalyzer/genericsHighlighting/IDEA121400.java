import java.util.*;

class Test<K,V> {
  private final Map<? extends K, ? extends V> m = null;

  {
    f(m.entrySet());
  }

  private static <A, B> void f(Set<? extends Map.Entry<? extends A, ? extends B>> s) {}

}

