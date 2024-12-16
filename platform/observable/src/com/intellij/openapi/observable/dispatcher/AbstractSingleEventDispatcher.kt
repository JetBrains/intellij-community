// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import com.intellij.openapi.util.Disposer
import com.intellij.util.containers.DisposableWrapperList
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicInteger

@ApiStatus.Internal
internal abstract class AbstractSingleEventDispatcher<T> : SingleEventDispatcher<T> {

  protected abstract fun addListener(ttl: Int?, parentDisposable: Disposable?, listener: ListenerWrapper<T, *>)

  override fun whenEventHappened(parentDisposable: Disposable?, listener: (T) -> Unit) {
    addListener(ttl = null, parentDisposable, ListenerWrapper.create(listener))
  }

  override fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: (T) -> Unit) {
    addListener(ttl = ttl, parentDisposable, ListenerWrapper.create(listener))
  }

  override fun onceWhenEventHappened(parentDisposable: Disposable?, listener: (T) -> Unit) {
    addListener(ttl = 1, parentDisposable, ListenerWrapper.create(listener))
  }

  override fun filterEvents(filter: (T) -> Boolean): SingleEventDispatcher<T> {
    return ChildDispatcher(this, map = { it }, filter = filter)
  }

  override fun ignoreParameters(): SingleEventDispatcher0 {
    return AbstractSingleEventDispatcher0.DelegateDispatcher(ChildDispatcher(this, map = { null }, filter = { true }))
  }

  override fun <T_out> mapParameters(map: (T) -> T_out): SingleEventDispatcher<T_out> {
    return ChildDispatcher(this, map = map, filter = { true })
  }

  @ApiStatus.Internal
  class RootDispatcher<T>
    : AbstractSingleEventDispatcher<T>(),
      SingleEventDispatcher.Multicaster<T> {

    private val listeners = DisposableWrapperList<ListenerWrapper<T, T>>()

    override fun fireEvent(parameter: T) {
      listeners
        .map { it.closure(parameter) }
        .filter { it.filter() }
        .forEach { it.invoke() }
    }

    override fun addListener(ttl: Int?, parentDisposable: Disposable?, listener: ListenerWrapper<T, *>) {
      val wrapper = ListenerWrapper.create<T>()
      if (parentDisposable != null) {
        wrapper.addFilter {
          @Suppress("DEPRECATION")
          !Disposer.isDisposed(parentDisposable)
        }
      }
      if (ttl != null) {
        require(ttl > 0)
        val ttlCounter = AtomicInteger(ttl)
        wrapper.addListener {
          if (ttlCounter.decrementAndGet() <= 0) {
            listeners.remove(wrapper)
          }
        }
      }
      wrapper.addFilter { listener.filter(it) }
      wrapper.addListener { listener.invoke(it) }
      when (parentDisposable) {
        null -> listeners.add(wrapper)
        else -> listeners.add(wrapper, parentDisposable)
      }
    }
  }

  @ApiStatus.Internal
  class ChildDispatcher<T_delegate, T>(
    private val parentDispatcher: AbstractSingleEventDispatcher<T_delegate>,
    private val map: (T_delegate) -> T,
    private val filter: (T) -> Boolean
  ) : AbstractSingleEventDispatcher<T>() {

    override fun addListener(ttl: Int?, parentDisposable: Disposable?, listener: ListenerWrapper<T, *>) {
      val wrapper = ListenerWrapper(map)
        .addFilter { it: T -> filter(it) && listener.filter(it) }
        .addListener { it: T -> listener.invoke(it) }
      parentDispatcher.addListener(ttl, parentDisposable, wrapper)
    }
  }

  protected class ListenerWrapper<T_delegate, T>(
    private val map: (T_delegate) -> T
  ) {

    private val filters = ArrayList<(T) -> Boolean>()
    private val listeners = ArrayList<(T) -> Unit>()

    fun addFilter(filter: (T) -> Boolean) = apply {
      filters.add(filter)
    }

    fun addListener(listener: (T) -> Unit) = apply {
      listeners.add(listener)
    }

    fun closure(parameter: T_delegate): ListenerWrapperClosure<T> {
      return ListenerWrapperClosure(map(parameter), filters, listeners)
    }

    fun filter(parameter: T_delegate): Boolean {
      return filters.all { it(map(parameter)) }
    }

    fun invoke(parameter: T_delegate) {
      listeners.forEach { it(map(parameter)) }
    }

    companion object {

      fun <T> create(): ListenerWrapper<T, T> {
        return ListenerWrapper { it }
      }

      fun <T> create(listener: (T) -> Unit): ListenerWrapper<T, T> {
        return create<T>().addListener(listener)
      }
    }
  }

  protected class ListenerWrapperClosure<T>(
    private val parameter: T,
    private val filters: List<(T) -> Boolean>,
    private val listeners: List<(T) -> Unit>
  ) {

    fun filter(): Boolean {
      return filters.all { it(parameter) }
    }

    fun invoke() {
      listeners.forEach { it(parameter) }
    }
  }

  companion object {

    @JvmStatic
    fun create(): SingleEventDispatcher0.Multicaster {
      return AbstractSingleEventDispatcher0.RootDispatcher()
    }

    @JvmStatic
    fun <T1> create(): SingleEventDispatcher.Multicaster<T1> {
      return RootDispatcher()
    }

    @JvmStatic
    fun <T1, T2> create2(): SingleEventDispatcher2.Multicaster<T1, T2> {
      return AbstractSingleEventDispatcher2.RootDispatcher()
    }
  }
}