// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"WhileLoopSpinsOnField", "SynchronizeOnThis"})
final class StorageGuard {
  private int myHolds;
  private int myWaiters;

  public interface StorageModeExitHandler {
    void leave();
  }

  private final StorageModeExitHandler myTrueStorageModeExitHandler = new StorageModeExitHandler() {
    @Override
    public void leave() {
      StorageGuard.this.leave(true);
    }
  };
  private final StorageModeExitHandler myFalseStorageModeExitHandler = new StorageModeExitHandler() {
    @Override
    public void leave() {
      StorageGuard.this.leave(false);
    }
  };

  @NotNull
  synchronized StorageModeExitHandler enter(boolean mode) {
    if (mode) {
      while (myHolds < 0) {
        doWait();
      }
      myHolds++;
      return myTrueStorageModeExitHandler;
    }
    else {
      while (myHolds > 0) {
        doWait();
      }
      myHolds--;
      return myFalseStorageModeExitHandler;
    }
  }

  private void doWait() {
    try {
      ++myWaiters;
      wait();
    }
    catch (InterruptedException ignored) {
    }
    finally {
      --myWaiters;
    }
  }

  private synchronized void leave(boolean mode) {
    myHolds += mode ? -1 : 1;
    if (myHolds == 0 && myWaiters > 0) {
      notifyAll();
    }
  }
}
