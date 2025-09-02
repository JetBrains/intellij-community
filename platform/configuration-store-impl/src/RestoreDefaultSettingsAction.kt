// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.configurationStore

import com.intellij.CommonBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.*
import com.intellij.openapi.application.ex.ApplicationEx
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.Messages
import com.intellij.platform.backend.workspace.GlobalWorkspaceModelCache
import java.nio.file.Path

private class RestoreDefaultSettingsAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    if (!confirmRestoreSettings(e, ConfigBackup.getNextBackupPath(PathManager.getConfigDir()))) {
      return
    }

    CustomConfigMigrationOption.StartWithCleanConfig.writeConfigMarkerFile()

    GlobalWorkspaceModelCache.getInstance()?.invalidateCaches()
    invokeLater {
      (ApplicationManager.getApplication() as ApplicationEx).restart(true)
    }
  }

  private fun confirmRestoreSettings(e: AnActionEvent, backupPath: Path?): Boolean {
    val restartButtonText =
      if (ApplicationManager.getApplication().isRestartCapable)
        ConfigurationStoreBundle.message("restore.default.settings.confirmation.button.restart")
      else ConfigurationStoreBundle.message("restore.default.settings.confirmation.button.shutdown")

    return Messages.YES == Messages.showYesNoDialog(
      e.project,
      ConfigurationStoreBundle.message("restore.default.settings.confirmation.message", backupPath),
      ConfigurationStoreBundle.message("restore.default.settings.confirmation.title"),
      restartButtonText,
      CommonBundle.getCancelButtonText(), Messages.getWarningIcon()
    )
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}