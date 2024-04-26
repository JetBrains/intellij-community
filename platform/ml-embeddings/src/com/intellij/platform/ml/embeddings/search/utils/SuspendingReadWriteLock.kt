// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

class SuspendingReadWriteLock {
  private val readLightswitch = Lightswitch()
  private val roomEmpty = Semaphore(1)

  suspend fun <T> read(action: suspend CoroutineScope.() -> T): T = coroutineScope {
    readLightswitch.lock(roomEmpty)
    return@coroutineScope try {
      action()
    }
    finally {
      readLightswitch.unlock(roomEmpty)
    }
  }

  suspend fun <T> write(action: suspend CoroutineScope.() -> T): T = coroutineScope {
    roomEmpty.acquire()
    return@coroutineScope try {
      action()
    }
    finally {
      roomEmpty.release()
    }
  }
}

private class Lightswitch {
  private var counter = 0
  private val mutex = Semaphore(1)

  suspend fun lock(semaphore: Semaphore) {
    mutex.withPermit {
      counter += 1
      if (counter == 1) {
        semaphore.acquire()
      }
    }
  }

  suspend fun unlock(semaphore: Semaphore) {
    mutex.withPermit {
      counter -= 1
      if (counter == 0) {
        semaphore.release()
      }
    }
  }
}