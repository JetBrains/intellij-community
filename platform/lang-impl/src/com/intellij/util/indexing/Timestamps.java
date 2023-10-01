// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;

import static java.nio.ByteOrder.BIG_ENDIAN;

/**
 * The class is meant to be accessed from synchronized block only
 */
@VisibleForTesting
@ApiStatus.Internal
public final class Timestamps {
  private static final boolean IS_UNIT_TEST = ApplicationManager.getApplication().isUnitTestMode();
  @VisibleForTesting
  @ApiStatus.Internal
  public static final FileAttribute PERSISTENCE = new FileAttribute("__index_stamps__", 2, false);
  private Object2LongMap<ID<?, ?>> myIndexStamps;
  private boolean myIsDirty = false;

  private interface InputAdapter {
    long readTime() throws IOException;

    boolean hasRemaining() throws IOException;

    int readInt() throws IOException;
  }

  @VisibleForTesting
  public static Timestamps readTimestamps(@Nullable DataInputStream stream) throws IOException {
    if (stream != null) {
      return new Timestamps(new InputAdapter() {
        @Override
        public long readTime() throws IOException {
          return DataInputOutputUtil.readTIME(stream);
        }

        @Override
        public boolean hasRemaining() throws IOException {
          return stream.available() > 0;
        }

        @Override
        public int readInt() throws IOException {
          return DataInputOutputUtil.readINT(stream);
        }
      });
    }
    else {
      return new Timestamps();
    }
  }

  public static Timestamps readTimestamps(@Nullable ByteBuffer buffer) throws IOException {
    if (buffer != null) {
      buffer.order(BIG_ENDIAN);//to be compatible with .writeToStream()
      return new Timestamps(new InputAdapter() {
        @Override
        public long readTime() {
          return DataInputOutputUtil.readTIME(buffer);
        }

        @Override
        public boolean hasRemaining() {
          return buffer.hasRemaining();
        }

        @Override
        public int readInt() {
          return DataInputOutputUtil.readINT(buffer);
        }
      });
    }
    else {
      return new Timestamps();
    }
  }

  private Timestamps() {
  }

  private Timestamps(final @NotNull InputAdapter stream) throws IOException {
    int[] outdatedIndices = null;
    //'header' is either timestamp (dominatingIndexStamp), or, if timestamp is small enough
    // (<MAX_SHORT), it is really a number of 'outdatedIndices', followed by actual indices
    // ints (which is index id from ID class), and followed by another timestamp=dominatingIndexStamp
    // value
    long dominatingIndexStamp = stream.readTime();
    long diff = dominatingIndexStamp - DataInputOutputUtil.timeBase;
    if (diff > 0 && diff < ID.MAX_NUMBER_OF_INDICES) {
      int numberOfOutdatedIndices = (int)diff;
      outdatedIndices = new int[numberOfOutdatedIndices];
      while (numberOfOutdatedIndices > 0) {
        outdatedIndices[--numberOfOutdatedIndices] = stream.readInt();
      }
      dominatingIndexStamp = stream.readTime();
    }

    //and after is just a set of ints -- Index IDs from ID class
    while (stream.hasRemaining()) {
      //RC: .findById() takes 1/4 of total the method time -- mostly spent on CHMap lookup.
      ID<?, ?> id = ID.findById(stream.readInt());
      if (id != null && !(id instanceof StubIndexKey)) {
        long stamp = IndexVersion.getIndexCreationStamp(id);
        if (stamp == 0) {
          continue; // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
        }
        if (myIndexStamps == null) {
          myIndexStamps = new Object2LongOpenHashMap<>(5, 0.98f);
        }
        if (stamp <= dominatingIndexStamp) {
          myIndexStamps.put(id, stamp);
        }
      }
    }

    if (outdatedIndices != null) {
      for (int outdatedIndexId : outdatedIndices) {
        ID<?, ?> id = ID.findById(outdatedIndexId);
        if (id != null && !(id instanceof StubIndexKey)) {
          if (IndexVersion.getIndexCreationStamp(id) == 0) {
            continue; // All (indices) IDs should be valid in this running session (e.g. we can have ID instance existing but index is not registered)
          }
          long stamp = IndexingStamp.INDEX_DATA_OUTDATED_STAMP;
          if (myIndexStamps == null) {
            myIndexStamps = new Object2LongOpenHashMap<>(5, 0.98f);
          }
          if (stamp <= dominatingIndexStamp) {
            myIndexStamps.put(id, stamp);
          }
        }
      }
    }
  }

  // Indexed stamp compact format:
  // (DataInputOutputUtil.timeBase + numberOfOutdatedIndices outdated_index_id+)? (dominating_index_stamp) index_id*
  // Note, that FSRecords.REASONABLY_SMALL attribute storage allocation policy will give an attribute 32 bytes to each file
  // Compact format allows 22 indexed states in this state
  void writeToStream(final DataOutputStream stream) throws IOException {
    if (myIndexStamps == null || myIndexStamps.isEmpty()) {
      DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase);
      return;
    }

    long dominatingStampIndex = 0;
    long numberOfOutdatedIndex = 0;
    List<Object2LongMap.Entry<ID<?, ?>>> entries = new ArrayList<>(myIndexStamps.object2LongEntrySet());
    entries.sort(Comparator.comparingInt(e -> e.getKey().getUniqueId()));

    for (Object2LongMap.Entry<ID<?, ?>> entry : entries) {
      long b = entry.getLongValue();
      if (b == IndexingStamp.INDEX_DATA_OUTDATED_STAMP) {
        ++numberOfOutdatedIndex;
        b = IndexVersion.getIndexCreationStamp(entry.getKey());
      }
      dominatingStampIndex = Math.max(dominatingStampIndex, b);

      if (IS_UNIT_TEST && b == IndexingStamp.HAS_NO_INDEXED_DATA_STAMP) {
        FileBasedIndexImpl.LOG.info("Wrong indexing timestamp state: " + myIndexStamps);
      }
    }

    if (numberOfOutdatedIndex > 0) {
      assert numberOfOutdatedIndex < ID.MAX_NUMBER_OF_INDICES;
      DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase + numberOfOutdatedIndex);
      for (Object2LongMap.Entry<ID<?, ?>> entry : entries) {
        if (entry.getLongValue() == IndexingStamp.INDEX_DATA_OUTDATED_STAMP) {
          DataInputOutputUtil.writeINT(stream, entry.getKey().getUniqueId());
        }
      }
    }

    DataInputOutputUtil.writeTIME(stream, dominatingStampIndex);
    for (Object2LongMap.Entry<ID<?, ?>> entry : entries) {
      if (entry.getLongValue() != IndexingStamp.INDEX_DATA_OUTDATED_STAMP) {
        DataInputOutputUtil.writeINT(stream, entry.getKey().getUniqueId());
      }
    }
  }

  long get(ID<?, ?> id) {
    return myIndexStamps != null ? myIndexStamps.getLong(id) : IndexingStamp.HAS_NO_INDEXED_DATA_STAMP;
  }

  void set(ID<?, ?> id, long tmst) {
    if (myIndexStamps == null) {
      myIndexStamps = new Object2LongOpenHashMap<>(5, 0.98f);
    }

    if (tmst == IndexingStamp.INDEX_DATA_OUTDATED_STAMP && !myIndexStamps.containsKey(id)) {
      return;
    }

    long previous = tmst == IndexingStamp.HAS_NO_INDEXED_DATA_STAMP ? myIndexStamps.removeLong(id) : myIndexStamps.put(id, tmst);
    if (previous != tmst) {
      myIsDirty = true;
    }
  }

  public boolean isDirty() {
    return myIsDirty;
  }

  @Override
  public String toString() {
    return "Timestamps{" +
           "indexStamps: " + myIndexStamps +
           ", dirty: " + myIsDirty +
           '}';
  }

  boolean hasIndexingTimeStamp() {
    return myIndexStamps != null && !myIndexStamps.isEmpty();
  }

  Collection<? extends ID<?, ?>> getIndexIds() {
    return myIndexStamps.keySet();
  }
}
