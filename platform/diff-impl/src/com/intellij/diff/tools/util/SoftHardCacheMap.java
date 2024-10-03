// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.tools.util;

import com.intellij.util.containers.ContainerUtil;
import com.intellij.util.containers.SLRUMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

@ApiStatus.Internal
public final class SoftHardCacheMap<K, V> {
  @NotNull private final SLRUMap<K, V> mySLRUMap;
  @NotNull private final Map<K, V> mySoftLinkMap = ContainerUtil.createSoftValueMap();

  public SoftHardCacheMap(final int protectedQueueSize, final int probationalQueueSize) {
    mySLRUMap = new SLRUMap<>(protectedQueueSize, probationalQueueSize);
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
