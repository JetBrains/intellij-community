// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.psi.stubs.StubIndexKey;
import com.intellij.util.io.DataInputOutputUtil;
import it.unimi.dsi.fastutil.objects.Object2LongMap;
import it.unimi.dsi.fastutil.objects.Object2LongOpenHashMap;
import it.unimi.dsi.fastutil.objects.ObjectSet;
import org.jetbrains.annotations.ApiStatus;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.VisibleForTesting;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Collection;

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

  @VisibleForTesting
  public Timestamps(@Nullable DataInputStream stream) throws IOException {
    if (stream != null) {
      int[] outdatedIndices = null;
      //'header' is either timestamp (dominatingIndexStamp), or, if timestamp is small enough
      // (<MAX_SHORT), it is really a number of 'outdatedIndices', followed by actual indices
      // ints (which is index id from ID class), and followed by another timestamp=dominatingIndexStamp
      // value
      long dominatingIndexStamp = DataInputOutputUtil.readTIME(stream);
      long diff = dominatingIndexStamp - DataInputOutputUtil.timeBase;
      if (diff > 0 && diff < ID.MAX_NUMBER_OF_INDICES) {
        int numberOfOutdatedIndices = (int)diff;
        outdatedIndices = new int[numberOfOutdatedIndices];
        while (numberOfOutdatedIndices > 0) {
          outdatedIndices[--numberOfOutdatedIndices] = DataInputOutputUtil.readINT(stream);
        }
        dominatingIndexStamp = DataInputOutputUtil.readTIME(stream);
      }

      //and after is just a set of ints -- Index IDs from ID class
      while (stream.available() > 0) {
        ID<?, ?> id = ID.findById(DataInputOutputUtil.readINT(stream));
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
  }

  public Timestamps(final @Nullable ByteBuffer buffer) {
    if (buffer != null) {
      buffer.order(BIG_ENDIAN);//to be compatible with .writeToStream()
      int[] outdatedIndices = null;
      //'header' is either timestamp (dominatingIndexStamp), or, if timestamp is small enough
      // (<MAX_SHORT), it is really a number of 'outdatedIndices', followed by actual indices
      // ints (which is index id from ID class), and followed by another timestamp=dominatingIndexStamp
      // value
      long dominatingIndexStamp = DataInputOutputUtil.readTIME(buffer);
      long diff = dominatingIndexStamp - DataInputOutputUtil.timeBase;
      if (diff > 0 && diff < ID.MAX_NUMBER_OF_INDICES) {
        int numberOfOutdatedIndices = (int)diff;
        outdatedIndices = new int[numberOfOutdatedIndices];
        while (numberOfOutdatedIndices > 0) {
          outdatedIndices[--numberOfOutdatedIndices] = DataInputOutputUtil.readINT(buffer);
        }
        dominatingIndexStamp = DataInputOutputUtil.readTIME(buffer);
      }

      //and after is just a set of ints -- Index IDs from ID class
      while (buffer.hasRemaining()) {
        //RC: .findById() takes 1/4 of total the method time -- mostly spent on CHMap lookup.
        ID<?, ?> id = ID.findById(DataInputOutputUtil.readINT(buffer));
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

    final long[] data = new long[2];
    final int dominatingStampIndex = 0;
    final int numberOfOutdatedIndex = 1;
    ObjectSet<Object2LongMap.Entry<ID<?, ?>>> entries = myIndexStamps.object2LongEntrySet();
    for (Object2LongMap.Entry<ID<?, ?>> entry : entries) {
      long b = entry.getLongValue();
      if (b == IndexingStamp.INDEX_DATA_OUTDATED_STAMP) {
        ++data[numberOfOutdatedIndex];
        b = IndexVersion.getIndexCreationStamp(entry.getKey());
      }
      data[dominatingStampIndex] = Math.max(data[dominatingStampIndex], b);

      if (IS_UNIT_TEST && b == IndexingStamp.HAS_NO_INDEXED_DATA_STAMP) {
        FileBasedIndexImpl.LOG.info("Wrong indexing timestamp state: " + myIndexStamps);
      }
    }

    if (data[numberOfOutdatedIndex] > 0) {
      assert data[numberOfOutdatedIndex] < ID.MAX_NUMBER_OF_INDICES;
      DataInputOutputUtil.writeTIME(stream, DataInputOutputUtil.timeBase + data[numberOfOutdatedIndex]);
      for (Object2LongMap.Entry<ID<?, ?>> entry : entries) {
        if (entry.getLongValue() == IndexingStamp.INDEX_DATA_OUTDATED_STAMP) {
          DataInputOutputUtil.writeINT(stream, entry.getKey().getUniqueId());
        }
      }
    }

    DataInputOutputUtil.writeTIME(stream, data[dominatingStampIndex]);
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
