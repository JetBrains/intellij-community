// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable

/**
 * Boolean property defines set and reset events.
 * Set event happens when value is changed from false to true.
 * Reset is opposite, it happens value is changed from true to false.
 */
interface ObservableBooleanProperty : ObservableProperty<Boolean> {

  /**
   * Subscribes on set event.
   * @param listener is called only when value is changed from false to true.
   * @param parentDisposable is used to early subscription from property set events.
   */
  fun afterSet(parentDisposable: Disposable?, listener: () -> Unit)
  fun afterSet(listener: () -> Unit) = afterSet(null, listener)

  /**
   * Subscribes on reset event.
   * @param listener is called only when value is changed from true to false.
   * @param parentDisposable is used to early subscription from property reset events.
   */
  fun afterReset(parentDisposable: Disposable?, listener: () -> Unit)
  fun afterReset(listener: () -> Unit) = afterReset(null, listener)
}