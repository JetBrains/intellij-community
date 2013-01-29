package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class HashInvertibleMap<K, V> implements InvertibleMap<K, V> {
    private static final Object NOT_ONCE_KEY = new Object();

    /**
     * in values save map k -> v
     *
     * keysMap is map v -> k, but if for v exist more that one key, k = NOT_ONCE_KEY
     * and keyLists.get(v) is list of keys
     *
     */

    private final Map<K, V> values = new HashMap<K, V>();
    private final Map<V, Object> keysMap = new HashMap<V, Object>();
    private final Map<V, Set<K>> keyLists = new HashMap<V, Set<K>>();

    @Override
    public void put(@NotNull K key, @NotNull V value) {
        remove(key);
        values.put(key, value);
        Object oldKey = keysMap.get(value);
        if (oldKey == null) {
            keysMap.put(value, key);
        } else {
            if (oldKey == NOT_ONCE_KEY) {
                Set<K> keyList = keyLists.get(value);
                assert keyList != null;
                keyList.add(key);
            } else {
                Set<K> keyList = new HashSet<K>(3);
                keyList.add((K) oldKey);
                keyList.add(key);
                keyLists.put(value, keyList);
                keysMap.put(value, NOT_ONCE_KEY);
            }
        }
    }

    @Override
    public V get(@NotNull K key) {
        return values.get(key);
    }

    @Override
    public V remove(@NotNull K key) {
        V value = values.remove(key);
        if (value == null) {
            return null;
        }
        Object oldKey = keysMap.get(value);
        assert oldKey != null;

        if (oldKey != NOT_ONCE_KEY) {
            keysMap.remove(value);
        } else {
            keyLists.get(value).remove(key);
        }
        return value;
    }

    @NotNull
    @Override
    public Set<K> getKeys(@NotNull V value) {
        Object key = keysMap.get(value);
        if (key == null) {
            return Collections.emptySet();
        }
        if (key == NOT_ONCE_KEY) {
            return Collections.unmodifiableSet(keyLists.get(value));
        }
        return new OneElementSet<K>((K) key);
    }

    private class OneElementSet<E> extends AbstractSet<E> {
        private final E element;

        private OneElementSet(E element) {
            this.element = element;
        }

        @Override
        public Iterator<E> iterator() {
            return new Iterator<E>() {
                boolean wasNextRequest = false;

                @Override
                public boolean hasNext() {
                    return !wasNextRequest;
                }

                @Override
                public E next() {
                    if (wasNextRequest) {
                        return null;
                    } else {
                        wasNextRequest = true;
                        return element;
                    }
                }

                @Override
                public void remove() {
                    throw new UnsupportedOperationException();
                }
            };
        }

        @Override
        public int size() {
            return 1;
        }
    }
}
