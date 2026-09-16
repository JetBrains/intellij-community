// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.http2Client

import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * Runs [action] for every item with at most [concurrency] of them at a time, inside the coroutine of the caller.
 *
 * The netty client of the compilation cache is a coroutine API, and a request must stay in the coroutine that owns
 * the connection. So this island keeps a coroutine fan-out; the blocking `forEachConcurrent` serves the rest of the build.
 */
internal suspend fun <T> Collection<T>.forEachConcurrentSuspending(concurrency: Int, action: suspend (T) -> Unit) {
  val permits = Semaphore(concurrency)
  coroutineScope {
    for (item in this@forEachConcurrentSuspending) {
      launch {
        permits.withPermit {
          action(item)
        }
      }
    }
  }
}
