// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.util.ThreadLocalCachedIntArray;
import com.intellij.psi.search.UsageSearchContext;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntOpenHashSet;
import it.unimi.dsi.fastutil.ints.IntSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;

import java.io.DataInput;
import java.io.DataOutput;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.Map;

/**
 * Externalizer for {@code Map<IdIndexEntry, Integer>} (for {@link IdIndex} forward index), specialized for the
 * case there actual map implementation is {@link IdEntryToScopeMap}, with primitives inside.
 * This allows optimizing away boxing, and deal with primitives directly.
 */
@ApiStatus.Internal
public class IdIndexEntryMapExternalizer implements DataExternalizer<Map<IdIndexEntry, Integer>> {

  private final DataExternalizer<Map<IdIndexEntry, Integer>> fallbackExternalizer;

  public IdIndexEntryMapExternalizer(@NotNull DataExternalizer<Map<IdIndexEntry, Integer>> externalizer) {
    fallbackExternalizer = externalizer;
  }

  private static final ThreadLocalCachedIntArray intsArrayPool = new ThreadLocalCachedIntArray();

  @Override
  public void save(@NotNull DataOutput out,
                   @NotNull Map<IdIndexEntry, Integer> entries) throws IOException {
    int size = entries.size();
    
    if (size == 0) {//fast path:
      DataInputOutputUtil.writeINT(out, size);
      return;
    }

    if (!(entries instanceof IdEntryToScopeMap idToScopeMap)) {
      fallbackExternalizer.save(out, entries);
      return;
    }

    DataInputOutputUtil.writeINT(out, size);

    //Store Map[IdHash -> ScopeMask] as inverted Map[ScopeMask -> List[IdHashes]] because sorted List[IdHashes] could
    // be stored with diff-compression, which is significant space reduction especially with long lists
    // (resulting binary format is fully compatible with that default InputMapExternalizer produces)

    Int2ObjectMap<IntSet> scopeMaskToHashes = new Int2ObjectOpenHashMap<>(8);
    idToScopeMap.forEach((idHash, scopeMask) -> {
      scopeMaskToHashes.computeIfAbsent(scopeMask, __ -> new IntOpenHashSet()).add(idHash);
      return true;
    });

    //MAYBE RC: use IntArrayList() instead of IntOpenHashSet() -- we sort the resulting set anyway, so we could
    //          very well skip duplicates after the sort, in O(N)
    for (int scopeMask : scopeMaskToHashes.keySet()) {
      out.writeByte(scopeMask & UsageSearchContext.ANY);

      IntSet idHashes = scopeMaskToHashes.get(scopeMask);
      int hashesCount = idHashes.size();
      if (hashesCount == 0) {
        throw new IllegalStateException("hashesCount(scope: " + scopeMask + ")(=" + hashesCount + ") must be > 0");
      }

      int[] buffer = intsArrayPool.getBuffer(hashesCount);
      idHashes.toArray(buffer);
      IdIndexEntriesExternalizer.save(out, buffer, hashesCount);
    }
  }

  @Override
  public @NotNull Map<IdIndexEntry, Integer> read(@NotNull DataInput in) throws IOException {
    int entriesCount = DataInputOutputUtil.readINT(in);
    if (entriesCount < 0) {
      throw new IOException("entriesCount: " + entriesCount + " must be >=0");
    }

    if (entriesCount == 0) {
      return Collections.emptyMap();
    }

    IdEntryToScopeMapImpl map = new IdEntryToScopeMapImpl(entriesCount);
    while (((InputStream)in).available() > 0) {
      int occurenceMask = in.readByte();

      //copied from IdIndexEntriesExternalizer
      int hashesCount = DataInputOutputUtil.readINT(in);
      if (hashesCount <= 0) {
        throw new IOException("hashesCount: " + hashesCount + " must be >0");
      }
      int prev = 0;
      while (hashesCount-- > 0) {
        int hash = (int)(DataInputOutputUtil.readLONG(in) + prev);
        map.updateMask(hash, occurenceMask);
        prev = hash;
      }
    }
    return map;
  }
}
