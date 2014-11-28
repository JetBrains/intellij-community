package com.intellij.openapi.util.diff.tools.util;

import com.intellij.util.containers.SLRUMap;
import com.intellij.util.containers.SoftValueHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public class SoftHardCacheMap<K, V> {
  @NotNull private final SLRUMap<K, V> mySLRUMap;
  @NotNull private final SoftValueHashMap<K, V> mySoftLinkMap;

  public SoftHardCacheMap(final int protectedQueueSize, final int probationalQueueSize) {
    mySLRUMap = new SLRUMap<K, V>(protectedQueueSize, probationalQueueSize);
    mySoftLinkMap = new SoftValueHashMap<K, V>();
  }

  @Nullable
  public V get(@NotNull K key) {
    V val = mySLRUMap.get(key);
    if (val != null) return val;

    val = mySoftLinkMap.get(key);
    if (val != null) mySLRUMap.put(key, val);

    return val;
  }

  public void put(@NotNull K key, @NotNull V value) {
    mySLRUMap.put(key, value);
    mySoftLinkMap.put(key, value);
  }

  public boolean remove(@NotNull K key) {
    boolean remove1 = mySLRUMap.remove(key);
    boolean remove2 = mySoftLinkMap.remove(key) != null;
    return remove1 || remove2;
  }

  public void clear() {
    mySLRUMap.clear();
    mySoftLinkMap.clear();
  }
}
