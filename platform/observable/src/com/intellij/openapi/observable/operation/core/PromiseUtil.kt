// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.concurrency.awaitPromise
import com.intellij.openapi.concurrency.waitForPromise
import com.intellij.openapi.observable.dispatcher.getPromise
import com.intellij.openapi.observable.util.getPromise
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.ThrowableComputable
import com.intellij.openapi.util.use
import kotlinx.coroutines.TimeoutCancellationException
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise
import java.util.concurrent.TimeoutException
import kotlin.coroutines.cancellation.CancellationException
import kotlin.time.Duration

fun ObservableOperationTrace.getOperationSchedulePromise(parentDisposable: Disposable): Promise<Nothing?> =
  scheduleObservable.getPromise(parentDisposable)

fun ObservableOperationTrace.getOperationStartPromise(parentDisposable: Disposable): Promise<Nothing?> =
  startObservable.getPromise(parentDisposable)

fun ObservableOperationTrace.getOperationFinishPromise(parentDisposable: Disposable): Promise<Nothing?> =
  finishObservable.getPromise(parentDisposable)

fun ObservableOperationTrace.getOperationCompletionPromise(parentDisposable: Disposable): Promise<Nothing?> =
  getPromise(parentDisposable) { disposable, listener ->
    withCompletedOperation(disposable) {
      listener(null)
    }
  }

fun <R> ObservableOperationTrace.waitForOperation(
  startTimeout: Duration,
  finishTimeout: Duration,
  action: ThrowableComputable<R, Throwable>
): R = waitForOperation(startTimeout, finishTimeout, { waitForPromise(it) }, { action.compute() })

suspend fun <R> ObservableOperationTrace.awaitOperation(
  startTimeout: Duration,
  finishTimeout: Duration,
  action: suspend () -> R
): R = waitForOperation(startTimeout, finishTimeout, { awaitPromise(it) }, { action() })

fun ObservableOperationTrace.waitForOperationCompletion(
  completionTimeout: Duration
): Unit = waitForOperationCompletion(completionTimeout) { waitForPromise(it) }

suspend fun ObservableOperationTrace.awaitOperationCompletion(
  completionTimeout: Duration
): Unit = waitForOperationCompletion(completionTimeout) { awaitPromise(it) }

@ApiStatus.Internal
inline fun <R> ObservableOperationTrace.waitForOperation(
  startTimeout: Duration,
  finishTimeout: Duration,
  wait: Promise<*>.(Duration) -> Unit,
  action: () -> R
): R {
  return Disposer.newDisposable("waitForOperation for $name").use { parentDisposable ->
    val startPromise = getOperationStartPromise(parentDisposable)
    val finishPromise = getOperationFinishPromise(parentDisposable)
    runCatching {
      action()
    }.onSuccess {
      runCatching { startPromise.wait(startTimeout) }
        .throwOnFailureAndWrapTimeout { "Operation '$name' didn't started during $startTimeout.\n$this" }
      runCatching { finishPromise.wait(finishTimeout) }
        .throwOnFailureAndWrapTimeout { "Operation '$name' didn't finished during $finishTimeout.\n$this" }
    }.onFailureCatching {
      runCatching { startPromise.wait(startTimeout) }
        .onSuccess {
          runCatching { finishPromise.wait(finishTimeout) }
            .throwOnFailureAndWrapTimeout { "Operation '$name' didn't finished during $finishTimeout.\n$this" }
        }
    }.getOrThrow()
  }
}

@ApiStatus.Internal
inline fun ObservableOperationTrace.waitForOperationCompletion(
  completionTimeout: Duration,
  wait: Promise<*>.(Duration) -> Unit,
) {
  return Disposer.newDisposable("waitForOperationCompletion for $name").use { parentDisposable ->
    val completionPromise = getOperationCompletionPromise(parentDisposable)
    runCatching { completionPromise.wait(completionTimeout) }
      .throwOnFailureAndWrapTimeout { "Operation '$name' didn't completed during $completionTimeout.\n$this" }
  }
}

@ApiStatus.Internal
private fun <T> Result<T>.wrapTimeoutException(lazyMessage: () -> String): Result<T> {
  return try {
    Result.success(getOrThrow())
  }
  catch (exception: TimeoutException) {
    Result.failure(OperationTimeoutException(lazyMessage(), exception))
  }
  catch (exception: TimeoutCancellationException) {
    Result.failure(OperationTimeoutException(lazyMessage(), exception))
  }
  catch (exception: Throwable) {
    Result.failure(exception)
  }
}

@ApiStatus.Internal
fun <T> Result<T>.throwOnFailureAndWrapTimeout(lazyMessage: () -> String) {
  wrapTimeoutException(lazyMessage).getOrThrow()
}

private class OperationTimeoutException(message: String, cause: Throwable) : CancellationException(message) {

  init {
    initCause(cause)
  }
}
