// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.util.concurrency

import com.intellij.openapi.progress.ProgressManager
import com.intellij.util.progress.withLockCancellable
import java.util.concurrent.atomic.AtomicReference
import java.util.concurrent.locks.ReentrantLock

/**
 * Lazy which has the following contracts:
 * - Thread-safe
 * - Cancellable (via ProgressManager.checkCanceled())
 * - Clearable (via [clear])
 * - Guaranteed to be initialized only once until [clear] is called
 * - [clear] is not blocking
 */
class CancellableClearableLazy<T>(private val initializer: () -> T) {
  private val computedValue = AtomicReference<Any>(NotInitialized())
  private val lock = ReentrantLock()

  @Suppress("UNCHECKED_CAST")
  private fun Any.unwrap(): T? =
    if (this is NotInitialized) null else this as T

  val value: T
    get() {
      computedValue.get().unwrap()?.let { return it }

      return lock.withLockCancellable {
        computeStateUnderLock()
      }
    }

  private fun computeStateUnderLock(): T {
    while (true) {
      val currentValue = computedValue.get()
      currentValue.unwrap()?.let { return it }

      val inferred = initializer()

      if (computedValue.compareAndSet(currentValue, inferred)) { // check if nobody called [clear]
        return inferred
      }
      ProgressManager.checkCanceled()
    }
  }

  fun clear() {
    computedValue.set(NotInitialized()) // install new instance of NotInitialized
  }

  private class NotInitialized // not object!
}