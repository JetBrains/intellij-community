// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Experimental
@ApiStatus.NonExtendable
interface SingleEventDispatcher<Listener> {

  /**
   * Subscribes listener on event. This listener will be automatically unsubscribed
   * when [parentDisposable] is disposed.
   *
   * @param listener is a listener function that will be called every time when event happens.
   * @param parentDisposable is used for early unsubscription when listener isn't called.
   */
  fun whenEventHappened(parentDisposable: Disposable?, listener: Listener)
  fun whenEventHappened(listener: Listener) = whenEventHappened(null, listener)

  /**
   * Subscribes listener with TTL on event. This listener will be automatically unsubscribed
   * when [ttl] is out or when [parentDisposable] is disposed. [parentDisposable] uses for
   * early unsubscription when listener is called less than [ttl] times.
   *
   * @param ttl is a number of listener calls which should be passed to unsubscribe listener.
   * @param listener is a listener function that will be called [ttl] times.
   * @param parentDisposable is a subscription disposable.
   */
  fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: Listener)
  fun whenEventHappened(ttl: Int, listener: Listener) = whenEventHappened(ttl, null, listener)

  /**
   * Subscribes listener on event. This listener will be unsubscribed immediately after execution.
   *
   * @param listener is a listener function that will be called only once.
   * @param parentDisposable is used for early unsubscription when listener isn't called.
   */
  fun onceWhenEventHappened(parentDisposable: Disposable?, listener: Listener)
  fun onceWhenEventHappened(listener: Listener) = onceWhenEventHappened(null, listener)

  @ApiStatus.NonExtendable
  interface Observable : SingleEventDispatcher<() -> Unit>

  @ApiStatus.NonExtendable
  interface Observable1<A1> : SingleEventDispatcher<(A1) -> Unit>

  @ApiStatus.NonExtendable
  interface Observable2<A1, A2> : SingleEventDispatcher<(A1, A2) -> Unit>

  @ApiStatus.NonExtendable
  interface Observable3<A1, A2, A3> : SingleEventDispatcher<(A1, A2, A3) -> Unit>

  @ApiStatus.NonExtendable
  interface Multicaster : Observable {

    fun fireEvent()
  }

  @ApiStatus.NonExtendable
  interface Multicaster1<A1> : Observable1<A1> {

    fun fireEvent(argument1: A1)
  }

  @ApiStatus.NonExtendable
  interface Multicaster2<A1, A2> : Observable2<A1, A2> {

    fun fireEvent(argument1: A1, argument2: A2)
  }

  companion object {

    @JvmStatic
    fun create(): Multicaster =
      SingleEventDispatcherImpl.create()

    @JvmStatic
    fun <A1> create(): Multicaster1<A1> =
      SingleEventDispatcherImpl.create<A1>()

    @JvmStatic
    fun <A1, A2> create2(): Multicaster2<A1, A2> =
      SingleEventDispatcherImpl.create2()
  }
}