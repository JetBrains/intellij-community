// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable

@Suppress("DEPRECATION")
interface BooleanProperty : ObservableClearableProperty<Boolean> {

  fun set()

  override fun reset()

  fun afterSet(listener: () -> Unit)

  fun afterSet(listener: () -> Unit, parentDisposable: Disposable)

  override fun afterReset(listener: () -> Unit)

  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable)
}