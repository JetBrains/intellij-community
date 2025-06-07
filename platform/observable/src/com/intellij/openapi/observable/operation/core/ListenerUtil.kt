// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:JvmName("ObservableOperationTraceUtil")

package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.Disposable
import com.intellij.util.ConcurrencyUtil

/**
 * Subscribes listener on operation schedule event.
 *
 * @see com.intellij.openapi.observable.dispatcher.SingleEventDispatcher.whenEventHappened
 */
fun ObservableOperationTrace.whenOperationScheduled(listener: () -> Unit): Unit = whenOperationScheduled(null, listener)
fun ObservableOperationTrace.whenOperationScheduled(parentDisposable: Disposable?, listener: () -> Unit): Unit =
  scheduleObservable.whenEventHappened(parentDisposable, listener)

/**
 * Subscribes listener with TTL on operation schedule event.
 *
 * @see com.intellij.openapi.observable.dispatcher.SingleEventDispatcher.whenEventHappened
 */
fun ObservableOperationTrace.whenOperationScheduled(ttl: Int, listener: () -> Unit): Unit = whenOperationScheduled(ttl, null, listener)
fun ObservableOperationTrace.whenOperationScheduled(ttl: Int, parentDisposable: Disposable?, listener: () -> Unit): Unit =
  scheduleObservable.whenEventHappened(ttl, parentDisposable, listener)

/**
 * Subscribes listener on operation schedule event that will be unsubscribed immediately before execution.
 *
 * @see com.intellij.openapi.observable.dispatcher.SingleEventDispatcher.onceWhenEventHappened
 */
fun ObservableOperationTrace.onceWhenOperationScheduled(listener: () -> Unit): Unit = onceWhenOperationScheduled(null, listener)
fun ObservableOperationTrace.onceWhenOperationScheduled(parentDisposable: Disposable?, listener: () -> Unit): Unit =
  scheduleObservable.onceWhenEventHappened(parentDisposable, listener)

/**
 * Subscribes listener on operation start event.
 *
 * @see com.intellij.openapi.observable.dispatcher.SingleEventDispatcher.whenEventHappened
 */
fun ObservableOperationTrace.whenOperationStarted(listener: () -> Unit): Unit = whenOperationStarted(null, listener)
fun ObservableOperationTrace.whenOperationStarted(parentDisposable: Disposable?, listener: () -> Unit): Unit =
  startObservable.whenEventHappened(parentDisposable, listener)

/**
 * Subscribes listener with TTL on operation start event.
 *
 * @see com.intellij.openapi.observable.dispatcher.SingleEventDispatcher.whenEventHappened
 */
fun ObservableOperationTrace.whenOperationStarted(ttl: Int, listener: () -> Unit): Unit = whenOperationStarted(ttl, null, listener)
fun ObservableOperationTrace.whenOperationStarted(ttl: Int, parentDisposable: Disposable?, listener: () -> Unit): Unit =
  startObservable.whenEventHappened(ttl, parentDisposable, listener)

/**
 * Subscribes listener on operation start event that will be unsubscribed immediately before execution.
 *
 * @see com.intellij.openapi.observable.dispatcher.SingleEventDispatcher.onceWhenEventHappened
 */
fun ObservableOperationTrace.onceWhenOperationStarted(listener: () -> Unit): Unit = onceWhenOperationStarted(null, listener)
fun ObservableOperationTrace.onceWhenOperationStarted(parentDisposable: Disposable?, listener: () -> Unit): Unit =
  startObservable.onceWhenEventHappened(parentDisposable, listener)

/**
 * Subscribes listener on operation finish event.
 *
 * @see com.intellij.openapi.observable.dispatcher.SingleEventDispatcher.whenEventHappened
 */
fun ObservableOperationTrace.whenOperationFinished(listener: () -> Unit): Unit = whenOperationFinished(null, listener)
fun ObservableOperationTrace.whenOperationFinished(parentDisposable: Disposable?, listener: () -> Unit): Unit =
  finishObservable.whenEventHappened(parentDisposable, listener)

/**
 * Subscribes listener with TTL on operation finish event.
 *
 * @see com.intellij.openapi.observable.dispatcher.SingleEventDispatcher.whenEventHappened
 */
fun ObservableOperationTrace.whenOperationFinished(ttl: Int, listener: () -> Unit): Unit = whenOperationFinished(ttl, null, listener)
fun ObservableOperationTrace.whenOperationFinished(ttl: Int, parentDisposable: Disposable?, listener: () -> Unit): Unit =
  finishObservable.whenEventHappened(ttl, parentDisposable, listener)

/**
 * Subscribes listener on operation finish event that will be unsubscribed immediately before execution.
 *
 * @see com.intellij.openapi.observable.dispatcher.SingleEventDispatcher.onceWhenEventHappened
 */
fun ObservableOperationTrace.onceWhenOperationFinished(listener: () -> Unit): Unit = onceWhenOperationFinished(null, listener)
fun ObservableOperationTrace.onceWhenOperationFinished(parentDisposable: Disposable?, listener: () -> Unit): Unit =
  finishObservable.onceWhenEventHappened(parentDisposable, listener)

fun ObservableOperationTrace.withScheduledOperation(action: () -> Unit): Unit = withScheduledOperation(null, action)
fun ObservableOperationTrace.withStartedOperation(action: () -> Unit): Unit = withStartedOperation(null, action)
fun ObservableOperationTrace.withCompletedOperation(listener: () -> Unit): Unit = withCompletedOperation(null, listener)

/**
 * Executed [listener] if or when operation is scheduled.
 *
 * @param listener is a listener function that will be called only once.
 * @param parentDisposable is used for early unsubscription when listener isn't called.
 */
fun ObservableOperationTrace.withScheduledOperation(parentDisposable: Disposable?, listener: () -> Unit) {
  val once = ConcurrencyUtil.once(listener)
  onceWhenOperationScheduled(parentDisposable) {
    once.run()
  }
  if (isOperationScheduled()) {
    once.run()
  }
}

/**
 * Executed [listener] if or when operation is started.
 *
 * @param listener is a listener function that will be called only once.
 * @param parentDisposable is used for early unsubscription when listener isn't called.
 */
fun ObservableOperationTrace.withStartedOperation(parentDisposable: Disposable?, listener: () -> Unit) {
  val once = ConcurrencyUtil.once(listener)
  onceWhenOperationStarted(parentDisposable) {
    once.run()
  }
  if (isOperationInProgress()) {
    once.run()
  }
}

/**
 * Executed [listener] if or when operation is completed.
 *
 * @param listener is a listener function that will be called only once.
 * @param parentDisposable is used for early unsubscription when listener isn't called.
 */
fun ObservableOperationTrace.withCompletedOperation(parentDisposable: Disposable?, listener: () -> Unit) {
  val once = ConcurrencyUtil.once(listener)
  onceWhenOperationFinished(parentDisposable) {
    once.run()
  }
  if (isOperationCompleted()) {
    once.run()
  }
}
