// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.execution.wsl

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.getOrLogException
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.blockingContext
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.progress.runBlockingCancellable
import kotlinx.coroutines.*
import org.jetbrains.annotations.ApiStatus.Internal
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

/**
 * Designed to be used only in [WSLDistribution] and is visible only for testing purposes.
 *
 * If you want to use some concurrent lazy value, consider trying [com.intellij.util.suspendingLazy] first.
 *
 * <cut>
 *
 * A lazy value that is guaranteed to be computed only on a pooled thread without a RA lock.
 * May be safely called from any thread.
 *
 * The calculation requires a parent job (see [runBlockingCancellable]),
 * and the calculation is canceled if the parent is canceled, no matter what thread the calculation was started on.
 */
@Internal
class WslDistributionSafeNullableLazyValue<T> private constructor(private val computable: Supplier<out T>) {
  private val deferred = AtomicReference<Deferred<T>?>(null)

  val isComputed: Boolean
    get() = true == deferred.get()?.isCompletedNormally()

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
  @OptIn(ExperimentalCoroutinesApi::class)
  fun getValueOrElse(notYet: T?): T? {
    val canWait = ApplicationManager.getApplication().run { !isDispatchThread && !isReadAccessAllowed }
    while (true) {
      val oldDeferred = deferred.get()
      return when {
        oldDeferred == null || oldDeferred.isCompletedExceptionally() -> {
          val newDeferred = CompletableDeferred<T>()
          when {
            !deferred.compareAndSet(oldDeferred, newDeferred) -> {
              continue
            }
            canWait -> {
              val result = runCatching {
                runBlockingCancellable {
                  blockingContext {
                    computable.get()
                  }
                }
              }
              newDeferred.completeWith(result)
              result.getOrThrow()
            }
            else -> {
              currentThreadCoroutineScope().launch(Dispatchers.IO) {
                blockingContext {
                  val result = runCatching {
                    computable.get()
                  }
                  newDeferred.completeWith(result)
                  // According to current logic, the error will never be re-thrown, so it should be logged explicitly.
                  result.getOrLogException(LOG)
                }
              }.invokeOnCompletion { cause: Throwable? ->
                if (cause != null && !newDeferred.isCompleted) {
                  newDeferred.completeExceptionally(cause)
                  if (LOG.isDebugEnabled) {
                    LOG.debug("Caching exceptional result to avoid hanging deferred", cause)
                  }
                }
              }
              notYet
            }
          }
        }
        canWait || oldDeferred.isCompleted -> {
          try {
            if (canWait) {
              runBlockingCancellable {
                oldDeferred.await()
              }
            }
            else {
              @Suppress("RAW_RUN_BLOCKING")  // The operation must return immediately.
              runBlocking {
                oldDeferred.await()
              }
            }
          }
          catch (ignored: Exception) {
            // Ignoring all errors to restart the computation. In particular, deliberately ignoring cancellation exceptions.
            // They may be caused by cancellation of some old computation.
            ProgressManager.checkCanceled()
            continue
          }
        }
        else -> {
          notYet
        }
      }
    }
  }

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun Deferred<T>.isCompletedNormally() = isCompleted && getCompletionExceptionOrNull() == null

  @OptIn(ExperimentalCoroutinesApi::class)
  private fun Deferred<T>.isCompletedExceptionally() = isCompleted && getCompletionExceptionOrNull() != null

  companion object {
    private val LOG = logger<WslDistributionSafeNullableLazyValue<*>>()

    @JvmStatic
    fun <T> create(computable: Supplier<out T>): WslDistributionSafeNullableLazyValue<T> {
      return WslDistributionSafeNullableLazyValue(computable)
    }
  }
}
