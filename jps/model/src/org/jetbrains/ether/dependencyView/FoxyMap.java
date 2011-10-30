package org.jetbrains.ether.dependencyView;

import org.jetbrains.ether.RW;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.util.*;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 08.03.11
 * Time: 15:38
 * To change this template use File | Settings | File Templates.
 */
class FoxyMap<K, V> implements Map<K, Object> {

  public static <X extends RW.Writable, Y extends RW.Writable> void write (final BufferedWriter w, final FoxyMap<X, Y> m){
    RW.writeln(w, Integer.toString(m.size()));

    for (Entry<X, Object> e : m.entrySet()) {
      e.getKey().write(w);

      final Object value = e.getValue();

      if (value instanceof Collection) {
        RW.writeln(w, "many");
        RW.writeln(w, (Collection<Y>) value);
      } else {
        RW.writeln(w, "single");
        ((Y) value).write(w);
      }
    }
  }

  public static <X, Y> FoxyMap<X,Y> read (final BufferedReader r, final RW.Reader<X> xr, final RW.Reader<Y> yr, final CollectionConstructor<Y> cc){
    final FoxyMap<X, Y> result = new FoxyMap<X,Y>(cc);

    final int size = RW.readInt(r);

    for (int i=0; i<size; i++) {
      final X key = xr.read(r);
      final String tag = RW.readString(r);

      if (tag.equals("many")) {
        result.put(key, RW.readMany(r, yr, cc.create()));
      } else {
        result.put(key, yr.read(r));
      }
    }

    return result;
  }

  public interface CollectionConstructor<X> {
    Collection<X> create();
  }

  private final Map<K, Object> map = new HashMap<K, Object>();

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

  public Object get(final Object key) {
    return map.get(key);
  }

  public Collection<V> foxyGet(final K key) {
    final Object c = get(key);

    if (c == null) {
      return null;
    }

    if (c instanceof Collection) {
      return (Collection)c;
    }

    final Collection<V> l = constr.create();

    l.add((V)c);

    return l;
  }

  public Object put(final K key, final Object value) {
    final Object c = get(key);

    if (c == null) {
      map.put(key, value);
    }
    else {
      if (c instanceof Collection) {
        if (value instanceof Collection) {
          ((Collection)c).addAll((Collection)value);
        }
        else {
          ((Collection)c).add(value);
        }
      }
      else {
        final Collection d = constr.create();

        d.add(c);

        if (value instanceof Collection) {
          d.addAll((Collection)value);
        }
        else {
          d.add(value);
        }

        map.put(key, d);
      }
    }

    return c;
  }

  public void removeFrom (final K key, final V value) {
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

  public Object remove(final Object key) {
    return map.remove(key);
  }

  public void putAll(Map<? extends K, ? extends Object> m) {
    for (Entry<? extends K, ? extends Object> e : m.entrySet()) {
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

  public Collection<Object> values() {
    final List l = new LinkedList();

    for (Object value : map.values()) {
      if (value instanceof Collection) {
        l.addAll((Collection)value);
      }
      else {
        l.add(value);
      }
    }

    return l;
  }

  public Collection<V> foxyValues() {
    return (Collection<V>)values();
  }

  public Set<Entry<K, Object>> entrySet() {
    return map.entrySet();
  }
}
