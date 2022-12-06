// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
internal class SingleEventDispatcherImpl<T> : SingleEventDispatcher.Multicaster1<T> {

  private val listeners = DisposableWrapperList<ListenerWrapper<T>>()

  override fun fireEvent(argument1: T) {
    listeners.forEach { it(argument1) }
  }

  override fun whenEventHappened(parentDisposable: Disposable?, listener: (T) -> Unit) {
    val wrapper = ListenerWrapper(parentDisposable) {
      listener(it)
    }
    addListener(parentDisposable, wrapper)
  }

  override fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: (T) -> Unit) {
    require(ttl > 0)
    val ttlCounter = AtomicInteger(ttl)
    val wrapper = ListenerWrapper(parentDisposable) { argument ->
      if (ttlCounter.decrementAndGet() == 0) {
        removeListener(this)
      }
      listener(argument)
    }
    addListener(parentDisposable, wrapper)
  }

  override fun onceWhenEventHappened(parentDisposable: Disposable?, listener: (T) -> Unit) {
    whenEventHappened(ttl = 1, parentDisposable, listener)
  }

  private fun addListener(parentDisposable: Disposable?, listener: ListenerWrapper<T>) {
    when (parentDisposable) {
      null -> listeners.add(listener)
      else -> listeners.add(listener, parentDisposable)
    }
  }

  private fun removeListener(listener: ListenerWrapper<T>) {
    listeners.remove(listener)
  }

  private class ListenerWrapper<T>(
    private val parentDisposable: Disposable?,
    private val listener: ListenerWrapper<T>.(T) -> Unit
  ) {

    operator fun invoke(argument: T) {
      @Suppress("DEPRECATION")
      if (parentDisposable == null || !Disposer.isDisposed(parentDisposable)) {
        listener(argument)
      }
    }
  }

  companion object {

    fun <A1> create(): SingleEventDispatcher.Multicaster1<A1> {
      return SingleEventDispatcherImpl()
    }

    fun create(): SingleEventDispatcher.Multicaster {
      return object : SingleEventDispatcher.Multicaster {

        private val eventDispatcher = SingleEventDispatcherImpl<Nothing?>()

        override fun fireEvent() =
          eventDispatcher.fireEvent(null)

        override fun whenEventHappened(parentDisposable: Disposable?, listener: () -> Unit) =
          eventDispatcher.whenEventHappened(parentDisposable) { listener() }

        override fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: () -> Unit) =
          eventDispatcher.whenEventHappened(ttl, parentDisposable) { listener() }

        override fun onceWhenEventHappened(parentDisposable: Disposable?, listener: () -> Unit) =
          eventDispatcher.onceWhenEventHappened(parentDisposable) { listener() }
      }
    }

    fun <A1, A2> create2(): SingleEventDispatcher.Multicaster2<A1, A2> {
      return object : SingleEventDispatcher.Multicaster2<A1, A2> {

        private val eventDispatcher = SingleEventDispatcherImpl<Pair<A1, A2>>()

        override fun fireEvent(argument1: A1, argument2: A2) =
          eventDispatcher.fireEvent(argument1 to argument2)

        override fun whenEventHappened(parentDisposable: Disposable?, listener: (A1, A2) -> Unit) =
          eventDispatcher.whenEventHappened(parentDisposable) { listener(it.first, it.second) }

        override fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: (A1, A2) -> Unit) =
          eventDispatcher.whenEventHappened(ttl, parentDisposable) { listener(it.first, it.second) }

        override fun onceWhenEventHappened(parentDisposable: Disposable?, listener: (A1, A2) -> Unit) =
          eventDispatcher.onceWhenEventHappened(parentDisposable) { listener(it.first, it.second) }
      }
    }
  }
}