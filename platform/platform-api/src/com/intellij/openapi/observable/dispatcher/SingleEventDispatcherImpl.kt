// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
internal abstract class SingleEventDispatcherImpl<Listener> : SingleEventDispatcher<Listener> {

  private val listeners = DisposableWrapperList<ListenerId<Listener>>()

  protected fun forEachListener(action: (Listener) -> Unit) {
    listeners.forEach {
      action(it.listener)
      it.action()
    }
  }

  private fun addListener(parentDisposable: Disposable?, listener: ListenerId<Listener>) {
    when (parentDisposable) {
      null -> listeners.add(listener)
      else -> listeners.add(listener, parentDisposable)
    }
  }

  private fun removeListener(id: Any) {
    listeners.remove(id)
  }

  override fun whenEventHappened(parentDisposable: Disposable?, listener: Listener) {
    addListener(parentDisposable, ListenerId(Any(), listener))
  }

  override fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: Listener) {
    val id = Any()
    val ttlCounter = TTLCounter(ttl) {
      removeListener(id)
    }
    addListener(parentDisposable, ListenerId(id, listener) {
      ttlCounter.update()
    })
  }

  override fun onceWhenEventHappened(parentDisposable: Disposable?, listener: Listener) {
    whenEventHappened(ttl = 1, parentDisposable, listener)
  }

  private class ListenerId<L>(val id: Any, val listener: L, val action: () -> Unit = {}) {
    override fun equals(other: Any?): Boolean {
      if (this === other) return true
      return other == id
    }

    override fun hashCode(): Int {
      return id.hashCode()
    }
  }

  /**
   * [action] will be called after TTL number of calls to [update].
   * Is used to subscribe a listener to be executed at most ttl times.
   *
   * @param ttl is the number of calls before disposal
   */
  private class TTLCounter(ttl: Int, val action: () -> Unit) {
    private val ttlCounter = AtomicInteger(ttl)

    fun update() {
      if (ttlCounter.decrementAndGet() == 0) {
        action()
      }
    }

    init {
      require(ttl > 0)
    }
  }

  class Multicaster : SingleEventDispatcherImpl<() -> Unit>(), SingleEventDispatcher.Multicaster {
    override fun fireEvent() {
      forEachListener { it() }
    }
  }

  class Multicaster1<A1> : SingleEventDispatcherImpl<(A1) -> Unit>(), SingleEventDispatcher.Multicaster1<A1> {
    override fun fireEvent(argument1: A1) {
      forEachListener { it(argument1) }
    }
  }

  class Multicaster2<A1, A2> : SingleEventDispatcherImpl<(A1, A2) -> Unit>(), SingleEventDispatcher.Multicaster2<A1, A2> {
    override fun fireEvent(argument1: A1, argument2: A2) {
      forEachListener { it(argument1, argument2) }
    }
  }
}