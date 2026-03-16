// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.logsUploader.LogPacker
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.CoreUiCoroutineScopeHolder
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.ui.IoErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

@ApiStatus.Internal
const val COLLECT_LOGS_NOTIFICATION_GROUP: String = "Collect Zipped Logs"

internal class CollectZippedLogsAction : AnAction(), DumbAware {
  private val CONFIRMATION_DIALOG = "zipped.logs.action.show.confirmation.dialog"

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
  override fun actionPerformed(e: AnActionEvent) = perform(e.project)

  @RequiresEdt
  fun perform(project: Project?) {
    val doNotShowDialog = PropertiesComponent.getInstance().getBoolean(CONFIRMATION_DIALOG)
    if (!doNotShowDialog) {
      val title = IdeBundle.message("collect.logs.sensitive.title")
      val message = IdeBundle.message("collect.logs.sensitive.text")
      val confirmed = MessageDialogBuilder.okCancel(title, message)
        .yesText(ActionsBundle.message("action.RevealIn.name.other", RevealFileAction.getFileManagerName()))
        .noText(CommonBundle.getCancelButtonText())
        .icon(Messages.getWarningIcon())
        .doNotAsk(object : DoNotAskOption.Adapter() {
          override fun rememberChoice(selected: Boolean, exitCode: Int) {
            PropertiesComponent.getInstance().setValue(CONFIRMATION_DIALOG, selected)
          }
        })
        .ask(project)
      if (!confirmed) {
        return
      }
    }

    service<CoreUiCoroutineScopeHolder>().coroutineScope.launch {
      collectLogs(project)
    }
  }

  private suspend fun collectLogs(project: Project? = null) {
    try {
      val logs =
        if (project == null) {
          withContext(Dispatchers.EDT) {
            runWithModalProgressBlocking(
              owner = ModalTaskOwner.guess(),
              title = @Suppress("DialogTitleCapitalization") IdeBundle.message("collect.logs.progress.title"),
              action = { LogPacker.packLogs(project) },
            )
          }
        }
        else {
          withBackgroundProgress(project, IdeBundle.message("collect.logs.progress.title")) {
            LogPacker.packLogs(project)
          }
        }

      if (RevealFileAction.isSupported()) {
        RevealFileAction.openFile(logs)
      }
      else {
        Notification(
          COLLECT_LOGS_NOTIFICATION_GROUP,
          IdeBundle.message("collect.logs.notification.success", logs),
          NotificationType.INFORMATION
        ).notify(project)
      }
    }
    catch (e: IOException) {
      thisLogger().warn(e)
      Notification(
        COLLECT_LOGS_NOTIFICATION_GROUP,
        IdeBundle.message("collect.logs.notification.error", IoErrorText.message(e)),
        NotificationType.ERROR
      ).notify(project)
    }
  }
}
