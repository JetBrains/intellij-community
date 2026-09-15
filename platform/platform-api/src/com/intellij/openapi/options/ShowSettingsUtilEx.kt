// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

// ShowSettingsUtil will be converted to Kotlin in 2025.3 and this interface will be removed.
@ApiStatus.Experimental
@ApiStatus.Internal
interface ShowSettingsUtilEx {
  /**
   * Must not be called from the UI context.
   */
  suspend fun showSettingsDialog(project: Project, groups: List<ConfigurableGroup>)

  /**
   * Shows the settings dialog of [project], and builds the configurable tree only when a dialog
   * must be created. An open settings window of the same project is reused, so no tree is built.
   *
   * Prefer this function over the overload that takes a built group list.
   *
   * Must not be called from the UI context.
   */
  suspend fun showSettingsDialog(project: Project)
}