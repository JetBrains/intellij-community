// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.getPromise1
import com.intellij.openapi.observable.util.getPromise2


fun SingleEventDispatcher.Observable.getPromise(parentDisposable: Disposable?) =
  com.intellij.openapi.observable.util.getPromise(parentDisposable, ::onceWhenEventHappened)

fun <A1> SingleEventDispatcher.Observable1<A1>.getPromise(parentDisposable: Disposable?) =
  getPromise1(parentDisposable, ::onceWhenEventHappened)

fun <A1, A2> SingleEventDispatcher.Observable2<A1, A2>.getPromise(parentDisposable: Disposable?) =
  getPromise2(parentDisposable, ::onceWhenEventHappened)