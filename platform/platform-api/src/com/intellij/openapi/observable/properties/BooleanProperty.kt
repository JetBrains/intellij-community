// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.observable.properties

import com.intellij.openapi.Disposable

/**
 * Boolean property provides api for mutable property.
 * You can subscribe on set and reset events.
 * Set event happens when value is changed from false to true.
 * Reset is opposite, it happens value is changed from true to false.
 */
@Suppress("DEPRECATION")
interface BooleanProperty : ObservableClearableProperty<Boolean> {

  /**
   * Sets property value to true.
   */
  fun set()

  /**
   * Resets property value to false.
   */
  @JvmDefault
  override fun reset()

  /**
   * Subscribes on set event.
   */
  fun afterSet(listener: () -> Unit)

  /**
   * Subscribes on set event.
   * @param listener is called only when value is changed from false to true.
   * @param parentDisposable is used to early subscription from property set events.
   */
  fun afterSet(listener: () -> Unit, parentDisposable: Disposable)

  /**
   * Subscribes on reset event.
   */
  @JvmDefault
  override fun afterReset(listener: () -> Unit)

  /**
   * Subscribes on reset event.
   * @param listener is called only when value is changed from true to false.
   * @param parentDisposable is used to early subscription from property reset events.
   */
  @JvmDefault
  override fun afterReset(listener: () -> Unit, parentDisposable: Disposable)
}