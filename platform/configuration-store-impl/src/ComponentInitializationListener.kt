// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.openapi.components.State
import org.jetbrains.annotations.ApiStatus
import com.intellij.openapi.components.PersistentStateComponent

/**
 * Allows listening for initialization of [PersistentStateComponent]s
 *
 * @see ComponentStoreImpl.addComponentInitializationListener
 */
@ApiStatus.Internal
interface ComponentInitializationListener {

  /**
   * Called when a [PersistentStateComponent] is initialized (after the [PersistentStateComponent.initializeComponent] method is called)
   *
   * @param stateSpec the [State] annotation of the component
   */
  fun onComponentInitialized(stateSpec: State?)

}
