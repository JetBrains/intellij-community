// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface SingleEventDispatcher<T> {

  /**
   * Subscribes listener on event. This listener will be automatically unsubscribed
   * when [parentDisposable] is disposed.
   *
   * @param listener is a listener function that will be called every time when event happens.
   * @param parentDisposable is used for early unsubscription when the listener isn't called.
   */
  fun whenEventHappened(parentDisposable: Disposable?, listener: (T) -> Unit)
  fun whenEventHappened(listener: (T) -> Unit): Unit = whenEventHappened(null, listener)

  /**
   * Subscribes listener with TTL on event. This listener will be automatically unsubscribed
   * when [ttl] is out or when [parentDisposable] is disposed. [parentDisposable] uses for
   * early unsubscription when listener is called less than [ttl] times.
   *
   * @param ttl is a number of listener calls which should be passed to unsubscribe listener.
   * @param listener is a listener function that will be called [ttl] times.
   * @param parentDisposable is a subscription disposable.
   */
  fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: (T) -> Unit)
  fun whenEventHappened(ttl: Int, listener: (T) -> Unit): Unit = whenEventHappened(ttl, null, listener)

  /**
   * Subscribes listener on event. This listener will be unsubscribed immediately after execution.
   *
   * @param listener is a listener function that will be called only once.
   * @param parentDisposable is used for early unsubscription when the listener isn't called.
   */
  fun onceWhenEventHappened(parentDisposable: Disposable?, listener: (T) -> Unit)
  fun onceWhenEventHappened(listener: (T) -> Unit): Unit = onceWhenEventHappened(null, listener)

  fun filterEvents(filter: (T) -> Boolean): SingleEventDispatcher<T>

  fun ignoreParameters(): SingleEventDispatcher0

  fun <R> mapParameters(map: (T) -> R): SingleEventDispatcher<R>

  @ApiStatus.NonExtendable
  interface Multicaster<T> : SingleEventDispatcher<T> {

    fun fireEvent(parameter: T)
  }

  companion object {

    @JvmStatic
    fun <T> create(): Multicaster<T> {
      return AbstractSingleEventDispatcher.RootDispatcher()
    }

    @JvmStatic
    fun create(): SingleEventDispatcher0.Multicaster {
      return AbstractSingleEventDispatcher0.RootDispatcher()
    }

    @JvmStatic
    fun <T1, T2> create2(): SingleEventDispatcher2.Multicaster<T1, T2> {
      return AbstractSingleEventDispatcher2.RootDispatcher()
    }
  }
}