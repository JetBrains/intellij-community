// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.collaboration.async

import com.intellij.collaboration.util.ProgressIndicatorsProvider
import com.intellij.execution.process.ProcessIOExecutorService
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.*
import java.util.concurrent.atomic.AtomicReference
import java.util.function.BiFunction
import java.util.function.Supplier

/**
 * Collection of utilities to use CompletableFuture and not care about CF and platform quirks
 */
object CompletableFutureUtil {

  /**
   * Check is the exception is a cancellation signal
   */
  fun isCancellation(error: Throwable): Boolean {
    return error is ProcessCanceledException
           || error is CancellationException
           || error is InterruptedException
           || error.cause?.let(::isCancellation) ?: false
  }

  /**
   * Extract actual exception from the one returned by completable future
   */
  fun extractError(error: Throwable): Throwable {
    return when (error) {
      is CompletionException -> extractError(error.cause!!)
      is ExecutionException -> extractError(error.cause!!)
      else -> error
    }
  }

  /**
   * Submit a [task] to IO thread pool under correct [ProgressIndicator]
   */
  fun <T> ProgressManager.submitIOTask(progressIndicator: ProgressIndicator,
                                       task: (indicator: ProgressIndicator) -> T): CompletableFuture<T> =
    submitIOTask(progressIndicator, false, task)

  /**
   * Submit a [task] to IO thread pool under correct [ProgressIndicator]
   */
  fun <T> ProgressManager.submitIOTask(progressIndicator: ProgressIndicator,
                                       cancelIndicatorOnFutureCancel: Boolean = false,
                                       task: (indicator: ProgressIndicator) -> T): CompletableFuture<T> =
    CompletableFuture.supplyAsync(Supplier { runProcess(Computable { task(progressIndicator) }, progressIndicator) },
                                  ProcessIOExecutorService.INSTANCE)
      .whenComplete { _, e: Throwable? ->
        if (cancelIndicatorOnFutureCancel && e != null && isCancellation(e) && !progressIndicator.isCanceled) {
          progressIndicator.cancel()
        }
      }

  /**
   * Submit a [task] to IO thread pool under correct [ProgressIndicator] acquired from [indicatorProvider] and release the indicator when task is completed
   */
  fun <T> ProgressManager.submitIOTask(indicatorProvider: ProgressIndicatorsProvider,
                                       task: (indicator: ProgressIndicator) -> T): CompletableFuture<T> =
    submitIOTask(indicatorProvider, false, task)

  /**
   * Submit a [task] to IO thread pool under correct [ProgressIndicator] acquired from [indicatorProvider] and release the indicator when task is completed
   */
  fun <T> ProgressManager.submitIOTask(indicatorProvider: ProgressIndicatorsProvider,
                                       cancelIndicatorOnFutureCancel: Boolean = false,
                                       task: (indicator: ProgressIndicator) -> T): CompletableFuture<T> {
    val indicator = indicatorProvider.acquireIndicator()
    return submitIOTask(indicator, cancelIndicatorOnFutureCancel, task).whenComplete { _, _ ->
      indicatorProvider.releaseIndicator(indicator)
    }
  }

  /**
   * Handle the result of async computation on EDT
   *
   * To allow proper GC the handler is cleaned up when [disposable] is disposed
   *
   * @param handler invoked when computation completes
   */
  fun <T> CompletableFuture<T>.handleOnEdt(disposable: Disposable,
                                           handler: (T?, Throwable?) -> Unit): CompletableFuture<Unit> {
    val handlerReference = AtomicReference(handler)
    Disposer.register(disposable, Disposable {
      handlerReference.set(null)
    })

    return handleAsync(BiFunction<T?, Throwable?, Unit> { result: T?, error: Throwable? ->
      val handlerFromRef = handlerReference.get() ?: throw ProcessCanceledException()
      handlerFromRef(result, error?.let { extractError(it) })
    }, getEDTExecutor(null))
  }

  /**
   * Handle the result of async computation on EDT
   *
   * @see [CompletableFuture.handle]
   * @param handler invoked when computation completes
   */
  fun <T, R> CompletableFuture<T>.handleOnEdt(modalityState: ModalityState? = null,
                                              handler: (T?, Throwable?) -> R): CompletableFuture<R> =
    handleAsync(BiFunction<T?, Throwable?, R> { result: T?, error: Throwable? ->
      handler(result, error?.let { extractError(it) })
    }, getEDTExecutor(modalityState))

  /**
   * Handle the result of async computation on EDT
   *
   * @see [CompletableFuture.thenApply]
   * @param handler invoked when computation completes without exception
   */
  fun <T, R> CompletableFuture<T>.successOnEdt(modalityState: ModalityState? = null, handler: (T) -> R): CompletableFuture<R> =
    handleOnEdt(modalityState) { result, error ->
      @Suppress("UNCHECKED_CAST")
      if (error != null) throw extractError(error) else handler(result as T)
    }

  /**
   * Handle the error on EDT
   *
   * If you need to return something after handling use [handleOnEdt]
   *
   * @see [CompletableFuture.exceptionally]
   * @param handler invoked when computation throws an exception which IS NOT [isCancellation]
   */
  fun <T> CompletableFuture<T>.errorOnEdt(modalityState: ModalityState? = null,
                                          handler: (Throwable) -> Unit): CompletableFuture<T> =
    handleOnEdt(modalityState) { result, error ->
      if (error != null) {
        val actualError = extractError(error)
        if (isCancellation(actualError)) throw ProcessCanceledException()
        handler(actualError)
        throw actualError
      }
      @Suppress("UNCHECKED_CAST")
      result as T
    }

  /**
   * Handle the cancellation on EDT
   *
   * @see [CompletableFuture.exceptionally]
   * @param handler invoked when computation throws an exception which IS [isCancellation]
   */
  fun <T> CompletableFuture<T>.cancellationOnEdt(modalityState: ModalityState? = null,
                                                 handler: (ProcessCanceledException) -> Unit): CompletableFuture<T> =
    handleOnEdt(modalityState) { result, error ->
      if (error != null) {
        val actualError = extractError(error)
        if (isCancellation(actualError)) handler(ProcessCanceledException())
        throw actualError
      }
      @Suppress("UNCHECKED_CAST")
      result as T
    }

  /**
   * Handled the completion of async computation on EDT
   *
   * @see [CompletableFuture.whenComplete]
   * @param handler invoked when computation completes successfully or throws an exception which IS NOT [isCancellation]
   */
  fun <T> CompletableFuture<T>.completionOnEdt(modalityState: ModalityState? = null,
                                               handler: () -> Unit): CompletableFuture<T> =
    handleOnEdt(modalityState) { result, error ->
      @Suppress("UNCHECKED_CAST")
      if (error != null) {
        if (!isCancellation(error)) handler()
        throw extractError(error)
      }
      else {
        handler()
        result as T
      }
    }

  fun getEDTExecutor(modalityState: ModalityState? = null) = Executor { runnable -> runInEdt(modalityState) { runnable.run() } }
}