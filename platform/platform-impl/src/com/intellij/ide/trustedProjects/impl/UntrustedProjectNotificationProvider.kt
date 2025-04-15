// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.ide.trustedProjects.TrustedProjectsDialog
import com.intellij.ide.trustedProjects.TrustedProjectsListener
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationProvider
import com.intellij.ui.EditorNotifications
import java.util.function.Function
import javax.swing.JComponent

class UntrustedProjectNotificationProvider : EditorNotificationProvider, DumbAware {
  override fun collectNotificationData(project: Project, file: VirtualFile): Function<in FileEditor, out JComponent?>? {
    if (TrustedProjects.isProjectTrusted(project)) return null

    return Function {
      UntrustedProjectEditorNotificationPanel(project, it) {
        if (TrustedProjectsDialog.confirmLoadingUntrustedProject(project)) {
          ApplicationManager.getApplication().messageBus
            .syncPublisher(TrustedProjectsListener.TOPIC)
            .onProjectTrustedFromNotification(project)
        }
      }
    }
  }

  internal class TrustedListener : TrustedProjectsListener {
    override fun onProjectTrusted(project: Project) {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }
}