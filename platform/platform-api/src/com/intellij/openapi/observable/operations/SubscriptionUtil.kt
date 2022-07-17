// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
@file:JvmName("SubscriptionUtil")
package com.intellij.openapi.observable.operations

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import java.util.concurrent.atomic.AtomicInteger

/**
 * Subscribes listener without arguments.
 * Unsubscribes when [parentDisposable] is disposed or [ttl] is out.
 *
 * @param ttl time to live
 * @param listener is a listener function that will be called [ttl] times
 * @param subscribe subscribes patched listener with provided disposable into your event system.
 * @param parentDisposable is subscription disposable.
 *
 * @see TTLCounter
 */
@JvmName("subscribe0")
fun subscribe(
  ttl: Int,
  listener: () -> Unit,
  subscribe: (() -> Unit, Disposable) -> Unit,
  parentDisposable: Disposable
) {
  val ttlCounter = TTLCounter(ttl, parentDisposable)
  subscribe({
    ttlCounter.update()
    listener()
  }, ttlCounter)
}

/**
 * Subscribes listener with one argument.
 * Unsubscribes when [parentDisposable] is disposed or [ttl] is out.
 *
 * @see TTLCounter
 */
@JvmName("subscribe1")
fun <A1> subscribe(
  ttl: Int,
  listener: (A1) -> Unit,
  subscribe: ((A1) -> Unit, Disposable) -> Unit,
  parentDisposable: Disposable
) {
  val ttlCounter = TTLCounter(ttl, parentDisposable)
  subscribe({ a1 ->
    ttlCounter.update()
    listener(a1)
  }, ttlCounter)
}

/**
 * Subscribes listener with two arguments.
 * Unsubscribes when [parentDisposable] is disposed or [ttl] is out.
 *
 * @see TTLCounter
 */
@JvmName("subscribe2")
fun <A1, A2> subscribe(
  ttl: Int,
  listener: (A1, A2) -> Unit,
  subscribe: ((A1, A2) -> Unit, Disposable) -> Unit,
  parentDisposable: Disposable
) {
  val ttlCounter = TTLCounter(ttl, parentDisposable)
  subscribe({ a1, a2 ->
    ttlCounter.update()
    listener(a1, a2)
  }, ttlCounter)
}

/**
 *  Disposable that will dispose itself after TTL number of calls to [update].
 *
 *  Is used to subscribe a listener to be executed at most ttl times.
 *
 * @param ttl is the number of calls before disposal
 * @param parentDisposable original subscription disposable.
 * This is used for early dispose when patched listener is called less than ttl times.
 *
 * @see com.intellij.openapi.observable.operations.subscribe(Int, () -> Unit, (() -> Unit, Disposable) -> Unit, Disposable)
 */
private class TTLCounter(ttl: Int, parentDisposable: Disposable) : Disposable {
  private val ttlCounter = AtomicInteger(ttl)

  fun update() {
    if (ttlCounter.decrementAndGet() == 0) {
      Disposer.dispose(this)
    }
  }

  override fun dispose() {}

  init {
    require(ttl > 0)
    Disposer.register(parentDisposable, this)
  }
}
