// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.core.customization

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

/**
 * Override this service to customize UI elements related to IDE's lifecycle. 
 * It is supposed to be overriden by parts of the platform used when the IDE is running in different modes, and isn't supposed to be 
 * overridden in plugins. 
 */
@ApiStatus.Internal
open class IdeLifecycleUiCustomization {
  /**
   * Returns `true` if an IDE can show confirmation dialog before exiting, and it can be switched off by the user. 
   * Returns `false` if no confirmation dialog should be shown.
   */
  open val canShowExitConfirmation: Boolean
    get() = true
  
  companion object {
    @JvmStatic
    fun getInstance(): IdeLifecycleUiCustomization = service()
  }
}
