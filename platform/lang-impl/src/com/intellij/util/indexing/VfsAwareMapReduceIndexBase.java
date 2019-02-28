// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.TransactionGuard;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.util.Processor;
import com.intellij.util.indexing.impl.*;
import gnu.trove.TIntObjectHashMap;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;
import java.util.Collection;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Lock;

public abstract class VfsAwareMapReduceIndexBase<Key, Value, Input> extends MapReduceIndex<Key, Value, Input> implements UpdatableIndex<Key, Value, Input> {

  static {
    if (!DebugAssertions.DEBUG) {
      final Application app = ApplicationManager.getApplication();
      DebugAssertions.DEBUG = app.isEAP() || app.isInternal();
    }
  }
  private final AtomicBoolean myInMemoryMode = new AtomicBoolean();
  private final TIntObjectHashMap<Map<Key, Value>> myInMemoryKeysAndValues = new TIntObjectHashMap<>();

  protected VfsAwareMapReduceIndexBase(@NotNull IndexExtension<Key, Value, Input> extension,
                                       @NotNull IndexStorage<Key, Value> storage,
                                       @Nullable ForwardIndex forwardIndex,
                                       @Nullable ForwardIndexAccessor<Key, Value, Input> forwardIndexAccessor) {
    super(extension, storage, forwardIndex, forwardIndexAccessor);
    installMemoryModeListener();
  }

  private void installMemoryModeListener() {
    IndexStorage<Key, Value> storage = getStorage();
    if (storage instanceof MemoryIndexStorage) {
      ((MemoryIndexStorage)storage).addBufferingStateListener(new MemoryIndexStorage.BufferingStateListener() {
        @Override
        public void bufferingStateChanged(boolean newState) {
          myInMemoryMode.set(newState);
        }

        @Override
        public void memoryStorageCleared() {
          synchronized (myInMemoryKeysAndValues) {
            myInMemoryKeysAndValues.clear();
          }
        }
      });
    }
  }

  public void removeTransientDataForKeys(int inputId, @Nullable Collection<? extends Key> keys) {
    if (keys == null) return;
    MemoryIndexStorage memoryIndexStorage = (MemoryIndexStorage)getStorage();
    for (Key key : keys) {
      memoryIndexStorage.clearMemoryMapForId(key, inputId);
    }
  }

  @Override
  protected void updateForwardIndex(int inputId, @NotNull Map<Key, Value> data, Input content) throws IOException {
    if (myInMemoryMode.get()) {
      synchronized (myInMemoryKeysAndValues) {
        myInMemoryKeysAndValues.put(inputId, data);
      }
    } else {
      super.updateForwardIndex(inputId, data, content);
    }
  }

  @NotNull
  @Override
  protected InputDataDiffBuilder<Key, Value> getKeysDiffBuilder(int inputId, Input content) throws IOException {
    if (myInMemoryMode.get()) {
      synchronized (myInMemoryKeysAndValues) {
        Map<Key, Value> keysAndValues = myInMemoryKeysAndValues.get(inputId);
        if (keysAndValues != null) {
          return new MapInputDataDiffBuilder<>(inputId, keysAndValues);
        }
      }
    }
    return super.getKeysDiffBuilder(inputId, content);
  }

  @Override
  public boolean processAllKeys(@NotNull Processor<? super Key> processor, @NotNull GlobalSearchScope scope, @Nullable IdFilter idFilter)
    throws StorageException {
    final Lock lock = getReadLock();
    lock.lock();
    try {
      return ((VfsAwareIndexStorage<Key, Value>)myStorage).processKeys(processor, scope, idFilter);
    }
    finally {
      lock.unlock();
    }
  }


  @Nullable
  @Override
  public Map<Key, Value> getAssociatedMap(int fileId) throws StorageException {
    throw new UnsupportedOperationException();
  }

  @Override
  public void setIndexedStateForFile(int fileId, @NotNull VirtualFile file) {
    IndexingStamp.setFileIndexedStateCurrent(fileId, (ID<?, ?>)myIndexId);
  }

  @Override
  public void resetIndexedStateForFile(int fileId) {
    IndexingStamp.setFileIndexedStateOutdated(fileId, (ID<?, ?>)myIndexId);
  }

  @Override
  public boolean isIndexedStateForFile(int fileId, @NotNull VirtualFile file) {
    return IndexingStamp.isFileIndexedStateCurrent(fileId, (ID<?, ?>)myIndexId);
  }

  @Override
  public void checkCanceled() {
    ProgressManager.checkCanceled();
  }

  @Override
  protected void requestRebuild(@NotNull Throwable ex) {
    Runnable action = () -> FileBasedIndex.getInstance().requestRebuild((ID<?, ?>)myIndexId, ex);
    Application application = ApplicationManager.getApplication();
    if (application.isUnitTestMode() || application.isHeadlessEnvironment()) {
      // avoid deadlock due to synchronous update in DumbServiceImpl#queueTask
      TransactionGuard.getInstance().submitTransactionLater(application, action);
    } else {
      action.run();
    }
  }
}
