// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.util.Map;

/**
 * Specialized Map from (identifier hash) to (occurence mask) ({@link com.intellij.psi.search.UsageSearchContext}),
 * for use in {@link IdIndex}.
 * <br/>
 * {@link IdIndex} is fundamentally (int,int), but formally it is (IdIndexEntry, Integer) -- and boxing creates quite
 * an overhead, given the index is one of the heaviest/most used indices.
 * We're trying to avoid boxing there possible, without compromising generic indexes API.
 * <br/>
 * This interface is one of such attempts: it extends {@code Map<IdIndexEntry, Integer>}, providing compatibility
 * with generic indexes API, but it assumes the underlying implementation actually uses primitives Map(int->int)
 * instead of generic Map(Object->Object) -- hence, we could provide additional methods for more effective access,
 * and {@link com.intellij.util.io.DataExternalizer} used by IdIndex could recognize this specific interface, and
 * utilize the optimized methods provided.
 * <br/>
 * Currently, there is only one such method: {@link #forEach(BiIntConsumer)} allows for more efficient iteration
 * over primitives, without boxing. But in future we may add more such methods, as needed.
 */
@ApiStatus.Internal
public interface IdEntryToScopeMap extends Map<IdIndexEntry, Integer> {
  void forEach(@NotNull BiIntConsumer consumer);

  @FunctionalInterface
  interface BiIntConsumer {
    /** @return true to continue iteration, false to stop */
    boolean consume(int idHash, int occurenceMask);
  }
}
