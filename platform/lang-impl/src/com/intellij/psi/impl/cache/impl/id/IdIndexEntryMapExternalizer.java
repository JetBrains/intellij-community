// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.impl.cache.impl.id;

import com.intellij.openapi.util.io.ByteArraySequence;
import com.intellij.util.io.DataExternalizer;
import com.intellij.util.io.DataInputOutputUtil;
import com.intellij.util.io.ToByteArraySequenceExternalizer;
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
public class IdIndexEntryMapExternalizer implements DataExternalizer<Map<IdIndexEntry, Integer>>,
                                                    ToByteArraySequenceExternalizer<Map<IdIndexEntry, Integer>> {

  /** Serialized form of an empty map: single '\0' byte (=map size, as varint, see {@link DataInputOutputUtil#writeINT(DataOutput, int)})) */
  private static final ByteArraySequence EMPTY_MAP_SERIALIZED = new ByteArraySequence(new byte[1]);

  private final DataExternalizer<Map<IdIndexEntry, Integer>> fallbackExternalizer;

  public IdIndexEntryMapExternalizer(@NotNull DataExternalizer<Map<IdIndexEntry, Integer>> externalizer) {
    fallbackExternalizer = externalizer;
  }

  @Override
  public void save(@NotNull DataOutput out,
                   @NotNull Map<IdIndexEntry, Integer> entries) throws IOException {
    int size = entries.size();

    if (size == 0) {//fast path:
      DataInputOutputUtil.writeINT(out, size);
      return;
    }

    if (!(entries instanceof IdEntryToScopeMapImpl idToScopeMap)) {
      fallbackExternalizer.save(out, entries);
      return;
    }

    //RC: actually, we shouldn't get here, since AbstractForwardIndexAccessor should use the .save(Map)->ByteArraySequence
    //    version for serializing IdEntryToScopeMapImpl -- but other use-cases may still call this method directly,
    //    so we keep this code here instead of throwing an exception.
    idToScopeMap.writeTo(out);
  }


  @Override
  public ByteArraySequence save(Map<IdIndexEntry, Integer> entries) throws IOException {
    if (entries.isEmpty()) {
      //Many IdIndexer impls use Collections.emptyMap() instead of IdDataConsumer.getResult() (=IdEntryToScopeMapImpl),
      // so we need to support this case specifically:
      return EMPTY_MAP_SERIALIZED;
    }
    
    if (entries instanceof IdEntryToScopeMapImpl idToScopeMap) {
      //return cached serialized form:
      return idToScopeMap.asByteArraySequence();
    }

    throw new IllegalStateException(entries + " must be an instance of IdEntryToScopeMapImpl");
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

      //decode diff-compressed array (see DataInputOutputUtil.writeDiffCompressed() for a format):
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
