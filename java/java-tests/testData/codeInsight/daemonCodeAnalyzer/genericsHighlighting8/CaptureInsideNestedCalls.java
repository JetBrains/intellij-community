import java.util.Iterator;

class Test<K, V> {

  private final Iterator<? extends Foo<? extends K, ? extends V>> i = null;

  public Foo<K, V> next() {
    return new Bar<>(i.next());
  }

  interface Foo<T, K> {}
  private static class Bar<K, V> implements Foo<K, V> {
    Bar(Foo<? extends K, ? extends V> e) {}
  }
}
