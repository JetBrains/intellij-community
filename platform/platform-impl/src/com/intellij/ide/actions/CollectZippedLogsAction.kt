// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:ApiStatus.Internal

package com.intellij.ide.actions

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.logsUploader.LogPacker.packLogs
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.EDT
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.progress.currentThreadCoroutineScope
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.okCancel
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.runWithModalProgressBlocking
import com.intellij.platform.ide.progress.withBackgroundProgress
import com.intellij.util.ui.IoErrorText
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.ApiStatus
import java.io.IOException

private const val CONFIRMATION_DIALOG = "zipped.logs.action.show.confirmation.dialog"
const val COLLECT_LOGS_NOTIFICATION_GROUP: String = "Collect Zipped Logs"

internal class CollectZippedLogsAction : AnAction(), DumbAware {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project

    val doNotShowDialog = PropertiesComponent.getInstance().getBoolean(CONFIRMATION_DIALOG)
    if (!doNotShowDialog) {
      val title = IdeBundle.message("collect.logs.sensitive.title")
      val message = IdeBundle.message("collect.logs.sensitive.text")
      val confirmed = okCancel(title, message)
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

    currentThreadCoroutineScope().launch {
      collectLogs(project)
    }
  }

  private suspend fun collectLogs(project: com.intellij.openapi.project.Project? = null) {
    try {
      val logs =
        if (project == null) {
          withContext(Dispatchers.EDT) {
            runWithModalProgressBlocking(
              owner = ModalTaskOwner.guess(),
              title = IdeBundle.message("collect.logs.progress.title"),
              action = { -> packLogs(project) },
            )
          }
        }
        else {
          withBackgroundProgress(
            project = project,
            title = IdeBundle.message("collect.logs.progress.title"),
            action = { -> packLogs(project) },
          )
        }

      if (RevealFileAction.isSupported()) {
        RevealFileAction.openFile(logs)
      }
      else {
        val notification = Notification(
          COLLECT_LOGS_NOTIFICATION_GROUP,
          IdeBundle.message("collect.logs.notification.success", logs),
          NotificationType.INFORMATION
        )
        notification.notify(project)
      }
    }
    catch (e: IOException) {
      thisLogger().warn(e)
      val notification = Notification(
        COLLECT_LOGS_NOTIFICATION_GROUP,
        IdeBundle.message("collect.logs.notification.error", IoErrorText.message(e)),
        NotificationType.ERROR
      )
      notification.notify(project)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}
