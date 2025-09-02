// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.core.customization

import com.intellij.openapi.components.service
import com.intellij.util.concurrency.annotations.RequiresBlockingContext
import org.jetbrains.annotations.ApiStatus

/**
 * Override this service to customize UI elements related to project's lifecycle.
 * It is supposed to be overriden by parts of the platform used when the IDE is running in different modes, and isn't supposed to be
 * overridden in plugins.
 */
@ApiStatus.Internal
open class ProjectLifecycleUiCustomization {
  /**
   * Returns `false` if the IDE should ask user whether to open a project in new windows or in the same window, and allow a user to choose 
   * the default choice in Settings. 
   * Returns `true` if the IDE should always open projects in a new window without asking for confirmation, and shouldn't allow changing 
   * this.
   */
  open val alwaysOpenProjectInNewWindow: Boolean
    get() = false

  /**
   * Returns `true` if an IDE can reopen the project which was opened in the previous IDE session, and it can be switched off by a user.
   * Returns `false` if this functionality should be disabled, and the user shouldn't be able to switch it on.  
   */
  open val canReopenProjectOnStartup: Boolean
    get() = true
  
  companion object {
    @JvmStatic
    @RequiresBlockingContext
    fun getInstance(): ProjectLifecycleUiCustomization = service()
  }
}
