// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.formatting.service

import com.intellij.CodeStyleBundle
import com.intellij.formatting.FormattingContext
import com.intellij.notification.Notification
import com.intellij.notification.NotificationType
import com.intellij.notification.Notifications
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.vfs.VirtualFile

internal object FormattingNotificationUtil {
  @JvmStatic
  fun reportErrorAndNavigate(
    project: Project,
    groupId: String,
    displayId: String?,
    title: @NlsContexts.NotificationTitle String,
    message: @NlsContexts.NotificationContent String,
    context: FormattingContext,
    offset: Int,
  ) {
    val virtualFile = context.virtualFile ?: return
    ApplicationManager.getApplication().invokeLater {
      val editors = FileEditorManager.getInstance(project).getEditors(virtualFile)
      if (editors.size > 0) {
        reportError(project, groupId, displayId, title, message)
        if (editors.any { it is TextEditor }) {
          navigateToFile(project, virtualFile, offset)
        }
      }
      else {
        val openFileAction = object : DumbAwareAction(CodeStyleBundle.message("formatting.service.open.file", virtualFile.name)) {
          override fun actionPerformed(e: AnActionEvent) {
            navigateToFile(project, virtualFile, offset)
          }
        }
        reportError(project, groupId, displayId, title, message, openFileAction)
      }
    }
  }

  @JvmStatic
  fun reportError(
    project: Project,
    groupId: String,
    displayId: String?,
    title: @NlsContexts.NotificationTitle String,
    message: @NlsContexts.NotificationContent String,
    vararg actions: AnAction,
  ) {
    val notification = Notification(groupId, title, message, NotificationType.ERROR)
    if (displayId != null) {
      notification.setDisplayId(displayId)
    }
    notification.addActions(actions.toList())
    Notifications.Bus.notify(notification, project)
  }
}

private fun navigateToFile(project: Project, file: VirtualFile, offset: Int) {
  val descriptor = OpenFileDescriptor(project, file, offset)
  FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
}