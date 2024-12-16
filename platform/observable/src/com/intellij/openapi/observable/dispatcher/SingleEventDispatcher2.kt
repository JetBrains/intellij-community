// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

/**
 * @see SingleEventDispatcher
 */
@ApiStatus.NonExtendable
interface SingleEventDispatcher2<T1, T2> {

  fun whenEventHappened(parentDisposable: Disposable?, listener: (T1, T2) -> Unit)
  fun whenEventHappened(listener: (T1, T2) -> Unit): Unit = whenEventHappened(null, listener)

  fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: (T1, T2) -> Unit)
  fun whenEventHappened(ttl: Int, listener: (T1, T2) -> Unit): Unit = whenEventHappened(ttl, null, listener)

  fun onceWhenEventHappened(parentDisposable: Disposable?, listener: (T1, T2) -> Unit)
  fun onceWhenEventHappened(listener: (T1, T2) -> Unit): Unit = onceWhenEventHappened(null, listener)

  fun filterEvents(filter: (T1, T2) -> Boolean): SingleEventDispatcher2<T1, T2>

  fun ignoreParameters(): SingleEventDispatcher0

  fun <R> mapParameters(map: (T1, T2) -> R): SingleEventDispatcher<R>

  fun <R1, R2> mapParameters(map: (T1, T2) -> Pair<R1, R2>): SingleEventDispatcher2<R1, R2>

  fun getDelegateDispatcher(): SingleEventDispatcher<Pair<T1, T2>>

  /**
   * @see SingleEventDispatcher.Multicaster
   */
  @ApiStatus.NonExtendable
  interface Multicaster<T1, T2> : SingleEventDispatcher2<T1, T2> {

    fun fireEvent(parameter1: T1, parameter2: T2)
  }
}