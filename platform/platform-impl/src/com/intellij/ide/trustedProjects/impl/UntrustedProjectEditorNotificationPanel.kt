// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.trustedProjects.impl

import com.intellij.ide.IdeBundle
import com.intellij.ide.impl.TRUSTED_PROJECTS_HELP_TOPIC
import com.intellij.ide.impl.TrustedProjectsStatistics
import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.help.HelpManager
import com.intellij.openapi.project.Project
import com.intellij.ui.EditorNotificationPanel

class UntrustedProjectEditorNotificationPanel(
  project: Project,
  fileEditor: FileEditor,
  onTrustProjectLinkClicked: () -> Unit
) : EditorNotificationPanel(fileEditor, Status.Warning) {

  init {
    text = IdeBundle.message("untrusted.project.notification.description")
    createActionLabel(IdeBundle.message("untrusted.project.notification.trust.link"), {
      TrustedProjectsStatistics.TRUST_PROJECT_FROM_BANNER.log(project)
      onTrustProjectLinkClicked()
    }, false)
    createActionLabel(IdeBundle.message("untrusted.project.notification.read.more.link"), {
      TrustedProjectsStatistics.READ_MORE_FROM_BANNER.log(project)
      HelpManager.getInstance().invokeHelp(TRUSTED_PROJECTS_HELP_TOPIC)
    }, false)
  }
}