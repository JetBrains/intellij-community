package org.hanuna.gitalk.common;

import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;

/**
 * @author erokhins
 */
public class CacheGet<K, V> implements Function<K, V> {
    private final Function<K, V> getFunction;
    private final int size;
    private Map<K, V> map;
    private Map<K, V> moreMap;

    public CacheGet(@NotNull Function<K, V> getFunction, int size) {
        this.getFunction = getFunction;
        this.size = size;
        this.map = new HashMap<K, V>(2 * size);
        this.moreMap = new HashMap<K, V>(2 * size);
    }

    public CacheGet(@NotNull Function<K, V> getFunction) {
        this(getFunction, 100);
    }

    public void clear() {
        this.map.clear();
        this.moreMap.clear();
    }

    @NotNull
    @Override
    public V get(@NotNull K key) {
        V value = moreMap.get(key);
        if (value != null) {
            return value;
        } else {
            value = getFunction.get(key);
            addToCache(key, value);
            return value;
        }
    }

    public boolean containsKey(@NotNull K key) {
        return moreMap.containsKey(key);
    }

    public void addToCache(@NotNull K key, @NotNull V value) {
        moreMap.put(key, value);
        map.put(key, value);
        checkSize();
    }

    private void checkSize() {
        if (map.size() >= size) {
            Map<K, V> tempMap = moreMap;
            moreMap = map;
            map = tempMap;
            map.clear();
        }
    }
}
