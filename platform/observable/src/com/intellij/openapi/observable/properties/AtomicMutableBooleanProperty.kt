// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

interface AtomicMutableBooleanProperty : MutableBooleanProperty, AtomicMutableProperty<Boolean> {

  /**
   * Atomically sets the value to [newValue] and returns the old value.
   *
   * @param newValue the new value
   * @return the previous value
   */
  fun getAndSet(newValue: Boolean): Boolean
}
