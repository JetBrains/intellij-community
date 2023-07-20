// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.dispatcher

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.getPromise
import org.jetbrains.concurrency.Promise

fun SingleEventDispatcher0.getPromise(parentDisposable: Disposable): Promise<Nothing?> =
  getDelegateDispatcher().getPromise(parentDisposable)

fun <T> SingleEventDispatcher<T>.getPromise(parentDisposable: Disposable): Promise<T> =
  getPromise(parentDisposable, ::onceWhenEventHappened)

fun <T1, T2> SingleEventDispatcher2<T1, T2>.getPromise(parentDisposable: Disposable): Promise<Pair<T1, T2>> =
  getDelegateDispatcher().getPromise(parentDisposable)