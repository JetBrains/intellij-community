// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore.defaults

import com.intellij.CommonBundle
import com.intellij.configurationStore.ConfigurationStoreBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ConfigBackup
import com.intellij.openapi.application.CustomConfigMigrationOption
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.Messages
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import com.intellij.util.application
import org.jetbrains.annotations.ApiStatus

/**
 * Contains common logic used in the monolith and remote development for restoring the default settings
 */
@ApiStatus.Internal
object DefaultSettingsHelper {

  /**
   * Performs all the actions necessary to restore the default settings:
   *
   * - Shows a confirmation dialog
   * - Initiates restoring the default settings
   * - Restarts the IDE
   *
   * @param project the project where the confirmation dialog should be shown
   */
  fun restoreDefaultSettings(project: Project?) {
    if (confirmRestoringDefaultSettings(project)) {
      initiateRestoringDefaultSettings()
      restart()
    }
  }

  /**
   * Shows a confirmation dialog for restoring the default settings
   *
   * @param project the project where the confirmation dialog should be shown
   */
  fun confirmRestoringDefaultSettings(project: Project?): Boolean {
    return confirmRestoringDefaultSettingsImpl(
      project,
      ConfigurationStoreBundle.message("restore.default.settings.confirmation.message", getNextBackupPath())
    )
  }

  /**
   * Shows a confirmation dialog for restoring the default settings on the frontend and backend
   *
   * @param project the project where the confirmation dialog should be shown
   * @param remoteBackupPath the path on the backend where a backup will be created
   */
  fun confirmRestoringDefaultSettingsOnFrontendAndBackend(project: Project?, remoteBackupPath: String): Boolean {
    return confirmRestoringDefaultSettingsImpl(
      project,
      ConfigurationStoreBundle.message("restore.default.settings.confirmation.message.controller", getNextBackupPath(), remoteBackupPath)
    )
  }

  /**
   * Initiates restoring the default settings by creating a marker file and invalidates caches
   */
  fun initiateRestoringDefaultSettings() {
    CustomConfigMigrationOption.StartWithCleanConfig.writeConfigMarkerFile()

    GlobalWorkspaceModelCache.getInstance()?.invalidateCaches()
  }

  /**
   * Restarts the IDE
   *
   * @param modalityState the modality state in which the IDE will be restarted
   */
  fun restart(modalityState: ModalityState = ModalityState.defaultModalityState()) {
    application.invokeLater(
      { (ApplicationManager.getApplication() as ApplicationEx).restart(true) },
      modalityState
    )
  }

  private fun confirmRestoringDefaultSettingsImpl(project: Project?, @NlsContexts.DialogMessage message: String): Boolean {
    val restartButtonText =
      if (ApplicationManager.getApplication().isRestartCapable)
        ConfigurationStoreBundle.message("restore.default.settings.confirmation.button.restart")
      else ConfigurationStoreBundle.message("restore.default.settings.confirmation.button.shutdown")

    return Messages.YES == Messages.showYesNoDialog(
      project,
      message,
      ConfigurationStoreBundle.message("restore.default.settings.confirmation.title"),
      restartButtonText,
      CommonBundle.getCancelButtonText(), Messages.getWarningIcon()
    )
  }

  private fun getNextBackupPath(): String {
    return ConfigBackup.getNextBackupPath(PathManager.getOriginalConfigDir()).toString()
  }

}
