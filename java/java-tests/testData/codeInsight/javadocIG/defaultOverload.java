import java.util.HashMap;

interface X<K, V> {
  /**
   * @param k foo
   * @param v bar
   */
  default V putIfAbsent(K k, V v) { return v; }
}

abstract class X1<K, V> implements X<K, V> {}

class X2<K, V> extends X1<K, V> {
  @Override
  public V putIfAbsent(K k, V v) { return super.putIfAbsent(k, v); }
}

class Foo implements X {
  @Override
  void foo(X2<String, Integer> x) {
    x.putIf<caret>Absent("foo", 0);
  }
}