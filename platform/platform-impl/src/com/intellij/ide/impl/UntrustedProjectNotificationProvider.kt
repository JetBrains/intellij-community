// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.impl

import com.intellij.ide.IdeBundle
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.EditorNotificationPanel
import com.intellij.ui.EditorNotifications

class UntrustedProjectNotificationProvider : EditorNotifications.Provider<EditorNotificationPanel?>(), DumbAware {
  override fun getKey() = KEY

  override fun createNotificationPanel(file: VirtualFile, fileEditor: FileEditor, project: Project): EditorNotificationPanel? {
    if (project.isTrusted()) {
      return null
    }
    return UntrustedProjectEditorNotificationPanel(project, fileEditor) {
      if (confirmLoadingUntrustedProject(
          project,
          IdeBundle.message("untrusted.project.general.dialog.title"),
          IdeBundle.message("untrusted.project.open.dialog.text", ApplicationInfoEx.getInstanceEx().fullApplicationName),
          IdeBundle.message("untrusted.project.dialog.trust.button"),
          IdeBundle.message("untrusted.project.dialog.distrust.button"))
      ) {
        ApplicationManager.getApplication().messageBus.syncPublisher(TrustStateListener.TOPIC).onProjectTrustedFromNotification(project)
      }
    }
  }

  companion object {
    private val KEY = Key.create<EditorNotificationPanel?>("UntrustedProjectNotification")
  }

  internal class TrustedListener : TrustStateListener {
    override fun onProjectTrusted(project: Project) {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }
}