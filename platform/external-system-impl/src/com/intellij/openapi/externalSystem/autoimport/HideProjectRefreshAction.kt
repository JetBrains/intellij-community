// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.project.DumbAwareAction

class HideProjectRefreshAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val notificationAware = ExternalSystemProjectNotificationAware.getInstance(project)
    notificationAware.notificationExpire()
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val notificationAware = ExternalSystemProjectNotificationAware.getInstance(project)
    e.presentation.isEnabled = notificationAware.isNotificationVisible()
  }

  init {
    templatePresentation.text = ExternalSystemBundle.message("external.system.reload.notification.action.hide.text")
    templatePresentation.icon = AllIcons.Actions.Close
    templatePresentation.hoveredIcon = AllIcons.Actions.CloseHovered
  }
}