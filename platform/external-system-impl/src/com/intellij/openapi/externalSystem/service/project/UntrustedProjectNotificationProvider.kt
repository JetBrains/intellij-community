// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.service.project

import com.intellij.CommonBundle
import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.TrustChangeNotifier
import com.intellij.ide.impl.isTrusted
import com.intellij.openapi.extensions.ExtensionPointName
import com.intellij.openapi.externalSystem.service.project.ExternalResolverIsSafe.executesTrustedCodeOnly
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil.confirmLoadingUntrustedProjectIfNeeded
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
    val provider = EP_NAME.findFirstSafe {
      !executesTrustedCodeOnly(it.systemId) &&
      it.shouldShowEditorNotification(project)
    } ?: return null
    return EditorNotificationPanel().apply {
      text = IdeBundle.message("untrusted.project.notification.desctription")
      createActionLabel(IdeBundle.message("untrusted.project.notification.trust.button", provider.systemId.readableName), {
        if (confirmLoadingUntrustedProjectIfNeeded(project, provider.systemId, CommonBundle.getCancelButtonText())) {
          provider.loadAllLinkedProjects(project)
        }
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