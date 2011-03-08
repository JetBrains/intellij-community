package org.jetbrains.ether.dependencyView;

import java.util.*;

/**
 * Created by IntelliJ IDEA.
 * User: db
 * Date: 08.03.11
 * Time: 15:38
 * To change this template use File | Settings | File Templates.
 */
public class FoxyMap<K, V> implements Map<K, Object> {

    public interface CollectionConstructor<X> {
        public Collection<X> create();
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
            return (Collection) c;
        }

        final Collection<V> l = constr.create();

        l.add((V) c);

        return l;
    }

    public Object put(final K key, final Object value) {
        final Object c = get(key);

        if (c == null) {
            map.put(key, value);
        } else {
            if (c instanceof Collection) {
                if (value instanceof Collection)
                    ((Collection) c).addAll((Collection) value);
                else
                    ((Collection) c).add(value);
            } else {
                final Collection d = constr.create();

                d.add(c);

                if (value instanceof Collection)
                    d.addAll((Collection) value);
                else
                    d.add(value);

                map.put(key, d);
            }
        }

        return c;
    }

    public Object remove(final Object key) {
        return map.remove(key);
    }

    public void putAll(Map<? extends K, ? extends Object> m) {
        for (Entry<? extends K, ? extends Object> e : m.entrySet()) {
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
                l.addAll((Collection) value);
            } else {
                l.add(value);
            }
        }

        return l;
    }

    public Collection<V> foxyValues() {
        return (Collection<V>) values();
    }

    public Set<Entry<K, Object>> entrySet() {
        return map.entrySet();
    }
}
