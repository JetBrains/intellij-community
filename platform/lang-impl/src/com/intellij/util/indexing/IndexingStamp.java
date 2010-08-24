/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.intellij.util.indexing;

import com.intellij.openapi.vfs.InvalidVirtualFileAccessException;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.FileAttribute;
import com.intellij.openapi.vfs.newvfs.NewVirtualFile;
import com.intellij.util.containers.SLRUCache;
import com.intellij.util.io.DataInputOutputUtil;
import gnu.trove.TObjectLongHashMap;
import gnu.trove.TObjectLongProcedure;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;

/**
 * @author Eugene Zhuravlev
 *         Date: Dec 25, 2007
 */
public class IndexingStamp {
  private IndexingStamp() {
  }

  /**
   * The class is meant to be accessed from synchronized block only 
   */
  private static class Timestamps {
    private static final FileAttribute PERSISTENCE = new FileAttribute("__index_stamps__", 1);
    private TObjectLongHashMap<ID<?, ?>> myIndexStamps;
    private boolean myIsDirty = false;

    public Timestamps(@Nullable DataInputStream stream) throws IOException {
      if (stream != null) {
        try {
          int count = DataInputOutputUtil.readINT(stream);
          if (count > 0) {
            myIndexStamps = new TObjectLongHashMap<ID<?, ?>>(20);
            for (int i = 0; i < count; i++) {
                ID<?, ?> id = ID.findById(DataInputOutputUtil.readINT(stream));
                long timestamp = DataInputOutputUtil.readTIME(stream);
                if (id != null) {
                  myIndexStamps.put(id, timestamp);
                }
              }
          }
        }
        finally {
          stream.close();
        }
      }
    }

    public void writeToStream(final DataOutputStream stream) throws IOException {
      if (myIndexStamps != null) {
        final int size = myIndexStamps.size();
        final int[] count = new int[]{0};
        DataInputOutputUtil.writeINT(stream, size);
        myIndexStamps.forEachEntry(new TObjectLongProcedure<ID<?, ?>>() {
          public boolean execute(final ID<?, ?> id, final long timestamp) {
            try {
              DataInputOutputUtil.writeINT(stream, (int)id.getUniqueId());
              DataInputOutputUtil.writeTIME(stream, timestamp);
              count[0]++;
              return true;
            }
            catch (IOException e) {
              throw new RuntimeException(e);
            }
          }
        });
        assert count[0] == size;
      }
    }

    public long get(ID<?, ?> id) {
      return myIndexStamps != null? myIndexStamps.get(id) : 0L;
    }

    public void set(ID<?, ?> id, long tmst) {
      try {
        if (myIndexStamps == null) {
          myIndexStamps = new TObjectLongHashMap<ID<?, ?>>(20);
        }
        myIndexStamps.put(id, tmst);
      }
      finally {
        myIsDirty = true;
      }
    }

    public boolean isDirty() {
      return myIsDirty;
    }
  }

  private static final SLRUCache<VirtualFile, Timestamps> myTimestampsCache = new SLRUCache<VirtualFile, Timestamps>(5, 5) {
    @NotNull
    public Timestamps createValue(final VirtualFile key) {
      try {
        final DataInputStream stream = Timestamps.PERSISTENCE.readAttribute(key);
        return new Timestamps(stream);
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }

    @Override
    protected void onDropFromCache(final VirtualFile key, final Timestamps value) {
      try {
        if (value.isDirty()) {
          final DataOutputStream sink = Timestamps.PERSISTENCE.writeAttribute(key);
          value.writeToStream(sink);
          sink.close();
        }
      }
      catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
      }
      catch (IOException e) {
        throw new RuntimeException(e);
      }
    }
  };

  public static boolean isFileIndexed(VirtualFile file, ID<?, ?> indexName, final long indexCreationStamp) {
    try {
      if (file instanceof NewVirtualFile && file.isValid()) {
        synchronized (myTimestampsCache) {
          return myTimestampsCache.get(file).get(indexName) == indexCreationStamp;
        }
      }
    }
    catch (RuntimeException e) {
      final Throwable cause = e.getCause();
      if (!(cause instanceof IOException)) {
        throw e; // in case of IO exceptions consider file unindexed
      }
    }

    return false;
  }

  public static void update(final VirtualFile file, final ID<?, ?> indexName, final long indexCreationStamp) {
    try {
      if (file instanceof NewVirtualFile && file.isValid()) {
        synchronized (myTimestampsCache) {
          myTimestampsCache.get(file).set(indexName, indexCreationStamp);
        }
      }
    }
    catch (InvalidVirtualFileAccessException ignored /*ok to ignore it here*/) {
    }
  }

  public static void flushCache() {
    synchronized (myTimestampsCache) {
      myTimestampsCache.clear();
    }
  }

}
