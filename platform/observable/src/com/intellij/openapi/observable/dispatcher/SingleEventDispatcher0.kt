// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.getPromise
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.concurrency.Promise

/**
 * @see SingleEventDispatcher
 */
@ApiStatus.NonExtendable
interface SingleEventDispatcher0 {
  fun whenEventHappened(parentDisposable: Disposable?, listener: () -> Unit)
  fun whenEventHappened(listener: () -> Unit): Unit = whenEventHappened(null, listener)

  fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: () -> Unit)
  fun whenEventHappened(ttl: Int, listener: () -> Unit): Unit = whenEventHappened(ttl, null, listener)

  fun onceWhenEventHappened(parentDisposable: Disposable?, listener: () -> Unit)
  fun onceWhenEventHappened(listener: () -> Unit): Unit = onceWhenEventHappened(null, listener)

  fun getDelegateDispatcher(): SingleEventDispatcher<Nothing?>

  /**
   * @see SingleEventDispatcher.Multicaster
   */
  @ApiStatus.NonExtendable
  interface Multicaster : SingleEventDispatcher0 {

    fun fireEvent()
  }
}

internal fun SingleEventDispatcher0.getPromise(parentDisposable: Disposable): Promise<Nothing?> {
  return getDelegateDispatcher().getPromise(parentDisposable)
}

internal fun <T> SingleEventDispatcher<T>.getPromise(parentDisposable: Disposable): Promise<T> {
  return getPromise(parentDisposable, ::onceWhenEventHappened)
}