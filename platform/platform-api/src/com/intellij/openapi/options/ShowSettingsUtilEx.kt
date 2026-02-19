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
}