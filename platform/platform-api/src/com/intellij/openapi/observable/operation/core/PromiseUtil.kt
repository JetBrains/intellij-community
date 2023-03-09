// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.operation.core

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.dispatcher.getPromise
import com.intellij.openapi.observable.util.getPromise


fun ObservableOperationTrace.getOperationSchedulePromise(parentDisposable: Disposable?) =
  scheduleObservable.getPromise(parentDisposable)

fun ObservableOperationTrace.getOperationStartPromise(parentDisposable: Disposable?) =
  startObservable.getPromise(parentDisposable)

fun ObservableOperationTrace.getOperationFinishPromise(parentDisposable: Disposable?) =
  finishObservable.getPromise(parentDisposable)

fun ObservableOperationTrace.getOperationPromise(parentDisposable: Disposable?) =
  getPromise(parentDisposable) { disposable, listener ->
    withCompletedOperation(disposable) {
      listener(null)
    }
  }