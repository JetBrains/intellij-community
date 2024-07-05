// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ml.embeddings.search.utils

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class SuspendingReadWriteLock {
  private val roomEmpty = Mutex()
  private var counter = 0
  private val counterMutex = Mutex()

  suspend fun <T> read(
    action: suspend CoroutineScope.() -> T,
  ): T = coroutineScope {
    counterMutex.withLock {
      if (++counter == 1) {
        roomEmpty.lock()
      }
    }
    try {
      action()
    }
    finally {
      withContext(NonCancellable) {
        counterMutex.withLock {
          if (--counter == 0) {
            roomEmpty.unlock()
          }
        }
      }
    }
  }

  suspend fun <T> write(
    action: suspend CoroutineScope.() -> T,
  ): T = coroutineScope {
    roomEmpty.withLock { action() }
  }
}