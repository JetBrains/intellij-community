// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.util.indexing;

import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;
import org.jetbrains.annotations.NotNull;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class StorageGuard {
  private final Lock myLock = new ReentrantLock();
  private final Condition myCondition = myLock.newCondition();

  private int myHolds;

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

  @SuppressWarnings("WhileLoopSpinsOnField")
  @NotNull
  StorageModeExitHandler enter(boolean mode) {
    boolean nonCancelableSection = ProgressManager.getInstance().isInNonCancelableSection();
    myLock.lock();
    try {
      if (mode) {
        while (myHolds < 0) {
          await(nonCancelableSection);
        }
        myHolds++;
        return myTrueStorageModeExitHandler;
      }
      else {
        while (myHolds > 0) {
          await(nonCancelableSection);
        }
        myHolds--;
        return myFalseStorageModeExitHandler;
      }
    }
    finally {
      myLock.unlock();
    }
  }

  private void await(boolean nonCancelableSection) {
    if (nonCancelableSection) {
      myCondition.awaitUninterruptibly();
    }
    else {
      ProgressIndicatorUtils.awaitWithCheckCanceled(myCondition);
    }
  }

  private void leave(boolean mode) {
    myLock.lock();
    try {
      myHolds += mode ? -1 : 1;
      if (myHolds == 0) {
        myCondition.signalAll();
      }
    }
    finally {
      myLock.unlock();
    }
  }
}
