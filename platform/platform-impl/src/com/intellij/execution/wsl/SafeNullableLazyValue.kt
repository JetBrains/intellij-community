// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.RecursionManager
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresBackgroundThread
import com.intellij.util.concurrency.annotations.RequiresReadLockAbsence
import java.util.concurrent.CompletableFuture
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.concurrent.Volatile

/**
 * A lazy value that is guaranteed to be computed only on a pooled thread without a RA lock.
 * May be safely called from any thread.
 */
internal class SafeNullableLazyValue<T> private constructor(private val computable: Supplier<out T>) {
  private val isComputedRef = AtomicReference(false) // null == in progress

  @Volatile
  private var value: T? = null

  val isComputed: Boolean
    get() = true == isComputedRef.get()

  private val isNotComputedNorInProgress: Boolean
    get() = false == isComputedRef.get()

  /**
   * Identical to `this.getValueOrElse(null)`.
   *
   * @see .getValueOrElse
   */
  fun getValue(): T? =
    getValueOrElse(null)

  /**
   * If possible, tries to compute this lazy value synchronously.
   * Otherwise, schedules an asynchronous computation if necessary and returns `notYet`.
   *
   * @return a computed nullable value, or `notYet`.
   * @implNote this method blocks on synchronous computation.
   * @see .getValue
   */
  fun getValueOrElse(notYet: T?): T? {
    if (isComputed) {
      return value
    }

    val app = ApplicationManager.getApplication()
    if (app.isDispatchThread || app.isReadAccessAllowed) {
      if (isNotComputedNorInProgress) {
        CompletableFuture.runAsync({ this.compute() }, AppExecutorUtil.getAppExecutorService())
      }
      return notYet
    }

    return compute()
  }

  @RequiresBackgroundThread
  @RequiresReadLockAbsence
  private fun compute(): T? {
    if (isComputed) {
      return value
    }
    synchronized(this) {
      if (isComputed) {
        return value
      }
      isComputedRef.set(null)
      try {
        val stamp = RecursionManager.markStack()
        val value = computable.get()
        if (stamp.mayCacheNow()) {
          this.value = value
          isComputedRef.set(true)
        }
        return value
      }
      finally {
        isComputedRef.compareAndSet(null, false)
      }
    }
  }

  companion object {
    @JvmStatic
    fun <T> create(computable: Supplier<out T>): SafeNullableLazyValue<T> {
      return SafeNullableLazyValue(computable)
    }
  }
}
