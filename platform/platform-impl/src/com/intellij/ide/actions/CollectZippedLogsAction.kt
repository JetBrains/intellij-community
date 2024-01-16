// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.logsUploader.LogsPacker.packLogs
import com.intellij.ide.util.PropertiesComponent
import com.intellij.idea.ActionsBundle
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.runBlockingCancellable
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.ui.DoNotAskOption
import com.intellij.openapi.ui.MessageDialogBuilder.Companion.okCancel
import com.intellij.openapi.ui.Messages
import com.intellij.platform.ide.progress.ModalTaskOwner
import com.intellij.platform.ide.progress.TaskCancellation
import com.intellij.platform.ide.progress.withModalProgress
import com.intellij.util.ui.IoErrorText
import java.io.IOException

class CollectZippedLogsAction : AnAction(), DumbAware {
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

    runBlockingCancellable {
      val modalTaskOwner = if (project == null) {
        ModalTaskOwner.guess()
      }
      else {
        ModalTaskOwner.project(project)
      }

      withModalProgress(modalTaskOwner, IdeBundle.message("collect.logs.progress.title"), TaskCancellation.cancellable()) {
        try {
          val logs = packLogs(project)
          if (RevealFileAction.isSupported()) {
            RevealFileAction.openFile(logs)
          }
          else {
            Notification(NOTIFICATION_GROUP, IdeBundle.message("collect.logs.notification.success", logs),
                         NotificationType.INFORMATION).notify(
              project)
          }
        }
        catch (x: IOException) {
          Logger.getInstance(javaClass).warn(x)
          val message = IdeBundle.message("collect.logs.notification.error", IoErrorText.message(x))
          Notification(NOTIFICATION_GROUP, message, NotificationType.ERROR).notify(project)
        }
      }
    }
  }


  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }

  companion object {
    private const val CONFIRMATION_DIALOG = "zipped.logs.action.show.confirmation.dialog"
    const val NOTIFICATION_GROUP: String = "Collect Zipped Logs"
  }
}
