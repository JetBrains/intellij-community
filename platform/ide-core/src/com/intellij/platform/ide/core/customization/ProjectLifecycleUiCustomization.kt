// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.core.customization

import com.intellij.openapi.components.service
import org.jetbrains.annotations.ApiStatus

/**
 * Override this service to customize UI elements related to project's lifecycle.
 * It is supposed to be overridden by parts of the platform used when the IDE is running in different modes and isn't supposed to be
 * overridden in plugins.
 */
@ApiStatus.Internal
open class ProjectLifecycleUiCustomization {

  @ApiStatus.Internal
  enum class ReopenProjectsOnStartupMode {
    USER_CONTROLLABLE, // Allows the user to control the behavior via settings
    ALWAYS, // Always open the last project. Hides the setting from the user in settings
    NEVER, // Never open the last project. Hides the setting from the user in settings
  }

  /**
   * Returns `false` if the IDE should ask user whether to open a project in new windows or in the same window, and allow a user to choose 
   * the default choice in Settings. 
   * Returns `true` if the IDE should always open projects in a new window without asking for confirmation, and shouldn't allow changing 
   * this.
   */
  open val alwaysOpenProjectInNewWindow: Boolean
    get() = false

  /**
   * Returns whether reopening the previously opened project on startup is user-configurable, always enabled, or disabled.
   */
  open val reopenProjectsOnStartupMode: ReopenProjectsOnStartupMode
    get() = ReopenProjectsOnStartupMode.USER_CONTROLLABLE
  
  companion object {
    @JvmStatic
    fun getInstance(): ProjectLifecycleUiCustomization = service()
  }
}
