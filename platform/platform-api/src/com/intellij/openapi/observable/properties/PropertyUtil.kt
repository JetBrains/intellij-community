// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable
import com.intellij.openapi.observable.util.whenDisposed


fun <T> ObservableMutableProperty<T>.set(value: T, parentDisposable: Disposable) {
  val oldValue = get()
  set(value)
  parentDisposable.whenDisposed {
    set(oldValue)
  }
}