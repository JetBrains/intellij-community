// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.indexing;

import com.intellij.openapi.util.Computable;
import com.intellij.util.SmartList;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

class StripedLock {
  private static final int LOCK_SIZE = 16;
  private final ReadWriteLock[] myLocks = new ReadWriteLock[LOCK_SIZE];

  StripedLock() {
    for (int i = 0; i < myLocks.length; ++i) myLocks[i] = new ReentrantReadWriteLock();
  }

  ReadWriteLock getLock(int fileId) {
    if (fileId < 0) fileId = -fileId;
    return myLocks[(fileId & 0xFF) % myLocks.length];
  }

  <T> T withReadLock(int fileId, @NotNull Computable<T> computable) {
    return withLock(fileId, computable, true);
  }

  <T> T withWriteLock(int fileId, @NotNull Computable<T> computable) {
    return withLock(fileId, computable, false);
  }

  private <T> T withLock(int fileId, @NotNull Computable<T> computable, boolean readLock) {
    ReadWriteLock readWriteLock = getLock(fileId);
    Lock lock = readLock ? readWriteLock.readLock() : readWriteLock.writeLock();
    lock.lock();
    try {
      return computable.compute();
    }
    finally {
      lock.unlock();
    }
  }

  <T> T withAllLocksReadLocked(@NotNull Computable<T> computable) {
    Lock[] locks = new Lock[myLocks.length];
    List<Exception> exceptions = new SmartList<>();
    boolean isComputed = false;
    T result = null;
    try {
      for (int i = 0; i < myLocks.length; i++) {
        locks[i] = myLocks[i].readLock();
        locks[i].lock();
      }
      result = computable.compute();
      isComputed = true;
    }
    catch (Exception e) {
      exceptions.add(e);
    }
    finally {
      for (Lock lock : locks) {
        if (lock == null) {
          break;
        }
        else {
          try {
            lock.unlock();
          }
          catch (Exception e) {
            exceptions.add(e);
          }
        }
      }
    }
    if (!exceptions.isEmpty()) {
      IllegalStateException exception = new IllegalStateException("Exceptions during unlocking");
      for (Exception subException : exceptions) {
        exception.addSuppressed(subException);
      }
      throw exception;
    }
    else {
      assert isComputed : "Computation in StripedLock.withAllLocksReadLocked was incorrect";
      return result;
    }
  }
}
