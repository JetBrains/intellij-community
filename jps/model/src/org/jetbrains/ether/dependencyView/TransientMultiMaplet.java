package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 08.03.11
 * Time: 15:38
 * To change this template use File | Settings | File Templates.
 */
class TransientMultiMaplet<K, V> implements MultiMaplet<K, V> {
  public static <X, Y> TransientMultiMaplet<X, Y> read(final BufferedReader r,
                                          final RW.Reader<X> xr,
                                          final RW.Reader<Y> yr,
                                          final CollectionConstructor<Y> cc) {
    final TransientMultiMaplet<X, Y> result = new TransientMultiMaplet<X, Y>(cc);

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

  private final Map<K, Collection<V>> myMap = new HashMap<K, Collection<V>>();

  private final CollectionConstructor<V> constr;

  public TransientMultiMaplet(final CollectionConstructor<V> c) {
    constr = c;
  }

  @Override
  public boolean containsKey(final K key) {
    return myMap.containsKey(key);
  }

  @Override
  public Collection<V> get(final K key) {
    return myMap.get(key);
  }

  @Override
  public void putAll(final MultiMaplet<K, V> m) {
    for (Map.Entry<K, Collection<V>> e : m.entrySet()) {
      put(e.getKey(), e.getValue());
    }
  }

  @Override
  public void put(final K key, final Collection<V> value) {
    final Collection<V> x = myMap.get(key);
    if (x == null) {
      myMap.put(key, value);
    }
    else {
      x.addAll(value);
    }
  }

  @Override
  public void replace(K key, Collection<V> value) {
    if (value == null || value.isEmpty()) {
      myMap.remove(key);
    }
    else {
      myMap.put(key, value);
    }
  }

  @Override
  public void put(final K key, final V value) {
    final Collection<V> x = constr.create();
    x.add(value);
    put(key, x);
  }

  @Override
  public void removeFrom(final K key, final V value) {
    final Collection<V> collection = myMap.get(key);
    if (collection != null) {
      if (collection.remove(value)) {
        if (collection.isEmpty()) {
          myMap.remove(key);
        }
      }
    }
  }

  @Override
  public void removeAll(K key, Collection<V> values) {
    final Collection<V> collection = myMap.get(key);
    if (collection != null) {
      if (collection.removeAll(values)) {
        if (collection.isEmpty()) {
          myMap.remove(key);
        }
      }
    }
  }

  @Override
  public void remove(final K key) {
    myMap.remove(key);
  }

  @Override
  public void replaceAll(MultiMaplet<K, V> m) {
    for (Map.Entry<K, Collection<V>> e : m.entrySet()) {
      replace(e.getKey(), e.getValue());
    }
  }

  @Override
  public Collection<K> keyCollection() {
    return myMap.keySet();
  }

  @Override
  public Set<Map.Entry<K, Collection<V>>> entrySet() {
    return myMap.entrySet();
  }

  @Override
  public void close(){
    myMap.clear(); // free memory
  }

  public void flush(boolean memoryCachesOnly) {
  }
}
