// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle

class HideProjectRefreshAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val notificationAware = ProjectNotificationAware.getInstance(project)
    notificationAware.hideNotification()
  }

  init {
    templatePresentation.text = ExternalSystemBundle.message("external.system.reload.notification.action.hide.text")
    templatePresentation.icon = AllIcons.Actions.Close
    templatePresentation.hoveredIcon = AllIcons.Actions.CloseHovered
  }
}