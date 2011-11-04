package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 08.03.11
 * Time: 15:38
 * To change this template use File | Settings | File Templates.
 */
class TransientMaplet<K, V> implements Maplet<K, V> {
  public static <X, Y> TransientMaplet<X, Y> read(final BufferedReader r,
                                          final RW.Reader<X> xr,
                                          final RW.Reader<Y> yr,
                                          final CollectionConstructor<Y> cc) {
    final TransientMaplet<X, Y> result = new TransientMaplet<X, Y>(cc);

    final int size = RW.readInt(r);

    for (int i = 0; i < size; i++) {
      final X key = xr.read(r);
      result.put(key, (Set<Y>)RW.readMany(r, yr, cc.create()));
    }

    return result;
  }

  public interface CollectionConstructor<X> {
    Collection<X> create();
  }

  private final Map<K, Collection<V>> map = new HashMap<K, Collection<V>>();

  private final CollectionConstructor<V> constr;

  public TransientMaplet(final CollectionConstructor<V> c) {
    constr = c;
  }

  @Override
  public boolean containsKey(final Object key) {
    return map.containsKey(key);
  }

  @Override
  public Collection<V> get(final Object key) {
    return map.get(key);
  }

  @Override
  public Collection<V> put(final K key, final Collection<V> value) {
    final Collection<V> x = map.get(key);

    if (x == null) {
      map.put(key, value);
    }
    else {
      x.addAll(value);
    }

    return x;
  }

  @Override
  public Collection<V> put(final K key, final V value) {
    final Collection<V> x = constr.create();
    x.add(value);
    return put(key, x);
  }

  @Override
  public void removeFrom(final K key, final V value) {
    final Object got = map.get(key);

    if (got != null) {
      if (got instanceof Collection) {
        ((Collection)got).remove(value);
      }
      else if (got.equals(value)) {
        map.remove(key);
      }
    }
  }

  @Override
  public Collection<V> remove(final Object key) {
    return map.remove(key);
  }

  @Override
  public void putAll(Maplet<K, V> m) {
    for (Map.Entry<K, Collection<V>> e : m.entrySet()) {
      remove(e.getKey());
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public Collection<K> keyCollection() {
    return map.keySet();
  }

  @Override
  public Set<Map.Entry<K, Collection<V>>> entrySet() {
    return map.entrySet();
  }

  @Override
  public void close(){

  }
}
