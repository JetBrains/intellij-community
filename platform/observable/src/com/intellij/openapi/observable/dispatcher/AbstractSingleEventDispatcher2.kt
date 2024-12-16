// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
internal abstract class AbstractSingleEventDispatcher2<T1, T2> : SingleEventDispatcher2<T1, T2> {

  protected abstract val delegate: SingleEventDispatcher<Pair<T1, T2>>

  override fun whenEventHappened(parentDisposable: Disposable?, listener: (T1, T2) -> Unit) {
    delegate.whenEventHappened(parentDisposable) { listener(it.first, it.second) }
  }

  override fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: (T1, T2) -> Unit) {
    delegate.whenEventHappened(ttl, parentDisposable) { listener(it.first, it.second) }
  }

  override fun onceWhenEventHappened(parentDisposable: Disposable?, listener: (T1, T2) -> Unit) {
    delegate.onceWhenEventHappened(parentDisposable) { listener(it.first, it.second) }
  }

  override fun filterEvents(filter: (T1, T2) -> Boolean): SingleEventDispatcher2<T1, T2> {
    return DelegateDispatcher(delegate.filterEvents { filter(it.first, it.second) })
  }

  override fun ignoreParameters(): SingleEventDispatcher0 {
    return delegate.ignoreParameters()
  }

  override fun <R> mapParameters(map: (T1, T2) -> R): SingleEventDispatcher<R> {
    return delegate.mapParameters { map(it.first, it.second) }
  }

  override fun <R1, R2> mapParameters(map: (T1, T2) -> Pair<R1, R2>): SingleEventDispatcher2<R1, R2> {
    return DelegateDispatcher(delegate.mapParameters { map(it.first, it.second) })
  }

  override fun getDelegateDispatcher(): SingleEventDispatcher<Pair<T1, T2>> {
    return delegate
  }

  @ApiStatus.Internal
  class RootDispatcher<T1, T2> : AbstractSingleEventDispatcher2<T1, T2>(),
                                 SingleEventDispatcher2.Multicaster<T1, T2> {

    override val delegate = AbstractSingleEventDispatcher.RootDispatcher<Pair<T1, T2>>()

    override fun fireEvent(parameter1: T1, parameter2: T2) {
      delegate.fireEvent(parameter1 to parameter2)
    }
  }

  @ApiStatus.Internal
  class DelegateDispatcher<T1, T2>(
    override val delegate: SingleEventDispatcher<Pair<T1, T2>>
  ) : AbstractSingleEventDispatcher2<T1, T2>()
}