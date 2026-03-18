// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.jps.dependency.impl;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.github.benmanes.caffeine.cache.LoadingCache;
import org.jetbrains.jps.dependency.ElementInterner;
import org.jetbrains.jps.dependency.ReferenceID;
import org.jetbrains.jps.dependency.Usage;

import java.util.List;

// Caffeine cache-based implementation. Provides in-memory deduplication for most frequently encountered graph data objects
public final class ElementInternerImpl implements ElementInterner {
  private static final int BASE_CACHE_SIZE = 512;

  private final LoadingCache<String, String> myStringInterner;
  private final LoadingCache<Usage, Usage> myUsageInterner;
  private final LoadingCache<ReferenceID, ReferenceID> myRefIdInterner;
  private final List<LoadingCache<?, ?>> allCaches;

  public ElementInternerImpl() {
    int maxGb = (int) (Runtime.getRuntime().maxMemory() / 1_073_741_824L);
    int factor = Math.min(Math.max(1, maxGb), 5); // increase by BASE_CACHE_SIZE for every additional Gb
    myStringInterner = Caffeine.newBuilder().maximumSize(3 * BASE_CACHE_SIZE * factor).build(key -> key);
    myUsageInterner = Caffeine.newBuilder().maximumSize(2 * BASE_CACHE_SIZE * factor).build(key -> key);
    myRefIdInterner = Caffeine.newBuilder().maximumSize(BASE_CACHE_SIZE * factor).build(key -> key);
    allCaches = List.of(myStringInterner, myUsageInterner, myRefIdInterner);
  }

  @Override
  public String intern(String str) {
    return str != null? myStringInterner.get(str) : null;
  }
  
  @Override
  public <T extends Usage> T intern(T usage) {
    //noinspection unchecked
    return usage != null? (T) myUsageInterner.get(usage) : null;
  }

  @Override
  public <T extends ReferenceID> T intern(T id) {
    //noinspection unchecked
    return id != null? (T) myRefIdInterner.get(id) : null;
  }

  @Override
  public void clear() {
    for (LoadingCache<?, ?> cache : allCaches) {
      cache.invalidateAll();
    }
  }
}
