// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build

import kotlinx.coroutines.channels.produce
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.concurrent.CancellationException

internal suspend fun <T> Collection<T>.forEachConcurrent(
  concurrency: Int = Runtime.getRuntime().availableProcessors(),
  action: suspend (T) -> Unit,
) {
  coroutineScope {
    val itemChannel = produce {
      for (item in this@forEachConcurrent) {
        send(item)
      }
    }

    repeat(concurrency) {
      launch {
        for (item in itemChannel) {
          try {
            action(item)
          }
          catch (@Suppress("IncorrectCancellationExceptionHandling") e: CancellationException) {
            if (coroutineContext.isActive) {
              // well, we are not canceled, only child
              throw IllegalStateException("Unexpected cancellation - action is cancelled itself", e)
            }
          }
        }
      }
    }
  }
}