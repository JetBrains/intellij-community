// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.util.Computable;
import com.intellij.util.indexing.events.VfsEventsMerger;
import org.jetbrains.annotations.NotNull;

import java.util.stream.Stream;

abstract class StorageBufferingHandler {
  private static final Logger LOG = Logger.getInstance(StorageBufferingHandler.class);
  private final StorageGuard myStorageLock = new StorageGuard();
  private volatile boolean myPreviousDataBufferingState;
  private final Object myBufferingStateUpdateLock = new Object();

  boolean runUpdate(boolean transientInMemoryIndices, @NotNull Computable<Boolean> update) {
    ProgressManager.checkCanceled();
    StorageGuard.StorageModeExitHandler storageModeExitHandler = myStorageLock.enter(transientInMemoryIndices);
    try {
      ensureBufferingState(transientInMemoryIndices);
      return update.compute();
    }
    finally {
      storageModeExitHandler.leave();
    }
  }

  private void ensureBufferingState(boolean transientInMemoryIndices) {
    if (myPreviousDataBufferingState != transientInMemoryIndices) {
      synchronized (myBufferingStateUpdateLock) {
        if (myPreviousDataBufferingState != transientInMemoryIndices) {
          getIndexes().forEach(index -> {
            try {
              index.setBufferingEnabled(transientInMemoryIndices);
            }
            catch (Exception e) {
              LOG.error(e);
            }
          });
          myPreviousDataBufferingState = transientInMemoryIndices;
          VfsEventsMerger.tryLog(() -> "New buffering state: " +
                                       (transientInMemoryIndices ? "transientInMemoryIndices" : "persistentIndices")
          );
        }
      }
    }
  }

  void resetState() {
    myPreviousDataBufferingState = false;
  }

  protected abstract @NotNull Stream<UpdatableIndex<?, ?, ?, ?>> getIndexes();
}
