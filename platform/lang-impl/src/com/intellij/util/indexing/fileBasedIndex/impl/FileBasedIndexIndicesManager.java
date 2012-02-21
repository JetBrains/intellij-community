/*
 * Copyright 2000-2012 JetBrains s.r.o.
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
package com.intellij.util.indexing.fileBasedIndex.impl;

import com.intellij.openapi.editor.Document;
import com.intellij.openapi.util.Pair;
import com.intellij.util.concurrency.Semaphore;
import com.intellij.util.containers.ConcurrentHashSet;
import com.intellij.util.indexing.*;
import gnu.trove.THashMap;

import java.util.Map;
import java.util.Set;

public class FileBasedIndexIndicesManager {
  private final ConcurrentHashSet<ID<?, ?>> myUpToDateIndices = new ConcurrentHashSet<ID<?, ?>>();
  private final Map<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, FileBasedIndex.InputFilter>> myIndices = new THashMap<ID<?, ?>, Pair<UpdatableIndex<?, ?, FileContent>, FileBasedIndex.InputFilter>>();
  private final PerIndexDocumentVersionMap myLastIndexedDocStamps = new PerIndexDocumentVersionMap();
  private final Map<ID<?, ?>, Semaphore> myUnsavedDataIndexingSemaphores = new THashMap<ID<?,?>, Semaphore>();

  public <K, V> UpdatableIndex<K, V, FileContent> getIndex(ID<K, V> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, FileBasedIndex.InputFilter> pair = myIndices.get(indexId);

    assert pair != null : "Index data is absent for index " + indexId;

    //noinspection unchecked
    return (UpdatableIndex<K,V, FileContent>)pair.getFirst();
  }

  public Set<ID<?, ?>> keySet() {
    return myIndices.keySet();
  }

  public int size() {
    return myIndices.size();
  }

  public <K, V> void addNewIndex(ID<K, V> key, Pair<UpdatableIndex<?, ?, FileContent>, FileBasedIndex.InputFilter> value) {
    myIndices.put(key, value);
    myUnsavedDataIndexingSemaphores.put(key, new Semaphore());
  }

  public FileBasedIndex.InputFilter getInputFilter(ID<?, ?> indexId) {
    final Pair<UpdatableIndex<?, ?, FileContent>, FileBasedIndex.InputFilter> pair = myIndices.get(indexId);

    assert pair != null : "Index data is absent for index " + indexId;

    return pair.getSecond();
  }

  public Semaphore getUnsavedDataIndexingSemaphore(ID<?,?> id) {
    return myUnsavedDataIndexingSemaphores.get(id);
  }

  public void invalidateUpToDate() {
    myUpToDateIndices.clear();
  }

  public boolean isUpToDate(ID<?, ?> id) {
    return myUpToDateIndices.contains(id);
  }

  public void addUpToDate(ID<?, ?> id) {
    myUpToDateIndices.add(id);
  }

  public void cleanupMemoryStorage() {
    myLastIndexedDocStamps.clear();
    for (ID<?, ?> indexId : myIndices.keySet()) {
      final MapReduceIndex index = (MapReduceIndex)getIndex(indexId);
      assert index != null;
      final MemoryIndexStorage memStorage = (MemoryIndexStorage)index.getStorage();
      index.getWriteLock().lock();
      try {
        memStorage.clearMemoryMap();
      }
      finally {
        index.getWriteLock().unlock();
      }
      memStorage.fireMemoryStorageCleared();
    }

  }

  public long getAndSetLastIndexedDocStamps(Document document, ID<?, ?> id, long stamp) {
    return myLastIndexedDocStamps.getAndSet(document, id, stamp);
  }

  public static class StorageGuard {
    private int myHolds = 0;

    public interface Holder {
      void leave();
    }

    private final Holder myTrueHolder = new Holder() {
      @Override
      public void leave() {
        StorageGuard.this.leave(true);
      }
    };
    private final Holder myFalseHolder = new Holder() {
      @Override
      public void leave() {
        StorageGuard.this.leave(false);
      }
    };

    public synchronized Holder enter(boolean mode) {
      if (mode) {
        while (myHolds < 0) {
          try {
            wait();
          }
          catch (InterruptedException ignored) {
          }
        }
        myHolds++;
        return myTrueHolder;
      }
      else {
        while (myHolds > 0) {
          try {
            wait();
          }
          catch (InterruptedException ignored) {
          }
        }
        myHolds--;
        return myFalseHolder;
      }
    }

    private synchronized void leave(boolean mode) {
      myHolds += mode? -1 : 1;
      if (myHolds == 0) {
        notifyAll();
      }
    }

  }

  private final FileBasedIndexIndicesManager.StorageGuard myStorageLock = new FileBasedIndexIndicesManager.StorageGuard();

  public FileBasedIndexIndicesManager.StorageGuard.Holder setDataBufferingEnabled(final boolean enabled) {
    final FileBasedIndexIndicesManager.StorageGuard.Holder holder = myStorageLock.enter(enabled);
    for (ID<?, ?> indexId : myIndices.keySet()) {
      final MapReduceIndex index = (MapReduceIndex)getIndex(indexId);
      assert index != null;
      final IndexStorage indexStorage = index.getStorage();
      ((MemoryIndexStorage)indexStorage).setBufferingEnabled(enabled);
    }
    return holder;
  }
}
