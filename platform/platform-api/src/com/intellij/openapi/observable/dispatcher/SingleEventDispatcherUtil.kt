// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable

fun <Listener> SingleEventDispatcher<Listener>.mapToObservable(transform: (() -> Unit) -> Listener) =
  mapListener(transform).toObservable()

fun <Listener, A1> SingleEventDispatcher<Listener>.mapToObservable1(transform: ((A1) -> Unit) -> Listener) =
  mapListener(transform).toObservable()

fun <Listener, A1, A2> SingleEventDispatcher<Listener>.mapToObservable2(transform: ((A1, A2) -> Unit) -> Listener) =
  mapListener(transform).toObservable()

fun <Listener, A1, A2, A3> SingleEventDispatcher<Listener>.mapToObservable3(transform: ((A1, A2, A3) -> Unit) -> Listener) =
  mapListener(transform).toObservable()

fun SingleEventDispatcher<() -> Unit>.toObservable(): SingleEventDispatcher.Observable =
  object : SingleEventDispatcher.Observable, SingleEventDispatcher<() -> Unit> by this {}

fun <A1> SingleEventDispatcher<(A1) -> Unit>.toObservable(): SingleEventDispatcher.Observable1<A1> =
  object : SingleEventDispatcher.Observable1<A1>, SingleEventDispatcher<(A1) -> Unit> by this {}

fun <A1, A2> SingleEventDispatcher<(A1, A2) -> Unit>.toObservable(): SingleEventDispatcher.Observable2<A1, A2> =
  object : SingleEventDispatcher.Observable2<A1, A2>, SingleEventDispatcher<(A1, A2) -> Unit> by this {}

fun <A1, A2, A3> SingleEventDispatcher<(A1, A2, A3) -> Unit>.toObservable(): SingleEventDispatcher.Observable3<A1, A2, A3> =
  object : SingleEventDispatcher.Observable3<A1, A2, A3>, SingleEventDispatcher<(A1, A2, A3) -> Unit> by this {}

fun <Listener, NewListener> SingleEventDispatcher<Listener>.mapListener(transform: (NewListener) -> Listener): SingleEventDispatcher<NewListener> =
  object : SingleEventDispatcher<NewListener> {
    override fun whenEventHappened(parentDisposable: Disposable?, listener: NewListener) =
      this@mapListener.whenEventHappened(parentDisposable, transform(listener))

    override fun whenEventHappened(ttl: Int, parentDisposable: Disposable?, listener: NewListener) =
      this@mapListener.whenEventHappened(ttl, parentDisposable, transform(listener))

    override fun onceWhenEventHappened(parentDisposable: Disposable?, listener: NewListener) =
      this@mapListener.onceWhenEventHappened(parentDisposable, transform(listener))
  }
