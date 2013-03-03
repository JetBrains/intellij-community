package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

import java.util.*;

/**
 * @author erokhins
 */
public class MultiMap<K, V> {
    public Map<K, List<V>> keyToListValues = new HashMap<K, List<V>>();


    public void put(@NotNull K key, @NotNull V value) {
        List<V> values = keyToListValues.get(key);
        if (values == null) {
            values = new ArrayList<V>();
            keyToListValues.put(key, values);
        }
        values.add(value);
    }

    public void remove(@NotNull K key, @NotNull V value) {
        List<V> values = keyToListValues.get(key);
        if (values != null) {
            values.remove(value);
        }
    }

    @NotNull
    public List<V> get(@NotNull K key) {
        List<V> values = keyToListValues.get(key);
        if (values != null) {
            return Collections.unmodifiableList(values);
        } else {
            return Collections.emptyList();
        }
    }

    public void clear() {
        keyToListValues.clear();
    }

}
