// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.options

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.util.concurrency.annotations.RequiresEdt
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

  /**
   * Closes the both modal and non-modal settings dialogs if they are opened.
   * Asks for confirmation with the given message if some settings need to be saved for closing.
   * Returns `null` if no settings dialog was opened, `true` if settings dialogs were successfully closed,
   * and `false` if closing failed due to validation errors or closing was canceled by a user.
   */
  suspend fun closeSettingsDialog(project: Project, @NlsContexts.DialogMessage message: String): Boolean?
}