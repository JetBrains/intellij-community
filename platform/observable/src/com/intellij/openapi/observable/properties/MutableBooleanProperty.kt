// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.observable.properties

/**
 * Boolean property with mutation functions.
 * @see ObservableBooleanProperty
 */
interface MutableBooleanProperty :
  ObservableBooleanProperty,
  ObservableMutableProperty<Boolean> {

  /**
   * Sets property value to true.
   */
  fun set()

  /**
   * Resets property value to false.
   */
  fun reset()
}