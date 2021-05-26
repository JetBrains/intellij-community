// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.ide.impl.TrustChangeNotifier
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.project.ExternalResolverIsSafe.executesTrustedCodeOnly
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.confirmLoadingUntrustedProject
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.help.HelpManager
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
    val providers = EP_NAME.extensions.filter { it.shouldShowEditorNotification(project) }
    val systemIds = providers.map { it.systemId }
    if (providers.isEmpty() || executesTrustedCodeOnly(systemIds)) {
      return null
    }
    return EditorNotificationPanel().apply {
      text = ExternalSystemBundle.message("untrusted.project.notification.description")
      createActionLabel(ExternalSystemBundle.message("untrusted.project.notification.trust.link"), {
        if (confirmLoadingUntrustedProject(project, systemIds)) {
          for (provider in providers) {
            provider.loadAllLinkedProjects(project)
          }
        }
      }, false)
      createActionLabel(ExternalSystemBundle.message("untrusted.project.notification.read.more.link"), {
        HelpManager.getInstance().invokeHelp("Project_security")
      }, false)
    }
  }

  companion object {
    private val EP_NAME = ExtensionPointName.create<UntrustedProjectModeProvider>("com.intellij.untrustedModeProvider")
    private val KEY = Key.create<EditorNotificationPanel?>("UntrustedProjectNotification")
  }

  class TrustedListener : TrustChangeNotifier {
    override fun projectTrusted(project: Project) {
      EditorNotifications.getInstance(project).updateAllNotifications()
    }
  }
}