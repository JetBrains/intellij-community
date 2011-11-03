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
class FoxyMap<K, V> implements Map<K, Collection<V>> {

  public static <X extends RW.Writable, Y extends RW.Writable> void write(final BufferedWriter w, final FoxyMap<X, Y> m) {
    RW.writeln(w, Integer.toString(m.size()));

    for (Entry<X, Collection<Y>> e : m.entrySet()) {
      e.getKey().write(w);
      RW.writeln(w, e.getValue());
    }
  }

  public static <X, Y> FoxyMap<X, Y> read(final BufferedReader r,
                                          final RW.Reader<X> xr,
                                          final RW.Reader<Y> yr,
                                          final CollectionConstructor<Y> cc) {
    final FoxyMap<X, Y> result = new FoxyMap<X, Y>(cc);

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

  public FoxyMap(final CollectionConstructor<V> c) {
    constr = c;
  }

  public int size() {
    return map.size();
  }

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public boolean containsKey(final Object key) {
    return map.containsKey(key);
  }

  public boolean containsValue(final Object value) {
    return map.containsValue(value);
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

  public Collection<V> put(final K key, final V value) {
    final Collection<V> x = constr.create();
    x.add(value);
    return put(key, x);
  }

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

  public Collection<V> remove(final Object key) {
    return map.remove(key);
  }

  @Override
  public void putAll(Map<? extends K, ? extends Collection<V>> m) {
    for (Entry<? extends K, ? extends Collection<V>> e : m.entrySet()) {
      remove(e.getKey());
      put(e.getKey(), e.getValue());
    }
  }

  public void clear() {
    map.clear();
  }

  public Set<K> keySet() {
    return map.keySet();
  }

  @Override
  public Collection<Collection<V>> values() {
    final List<Collection<V>> l = new LinkedList<Collection<V>>();

    for (Collection<V> value : map.values()) {
      l.add(value);
    }

    return l;
  }

  public Set<Entry<K, Collection<V>>> entrySet() {
    return map.entrySet();
  }
}
