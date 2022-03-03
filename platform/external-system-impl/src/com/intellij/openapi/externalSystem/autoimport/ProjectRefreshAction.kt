// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.openapi.externalSystem.autoimport

import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationNamesInfo
import com.intellij.openapi.externalSystem.model.ProjectSystemId
import com.intellij.openapi.externalSystem.ui.ExternalSystemIconProvider
import com.intellij.openapi.externalSystem.util.ExternalSystemBundle
import com.intellij.openapi.externalSystem.util.ExternalSystemUtil
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsActions
import javax.swing.Icon

class ProjectRefreshAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    refreshProject(project)
  }

  override fun update(e: AnActionEvent) {
    val project = e.project ?: return
    val notificationAware = ExternalSystemProjectNotificationAware.getInstance(project)
    val systemIds = notificationAware.getSystemIds()
    if (systemIds.isNotEmpty()) {
      e.presentation.text = getNotificationText(systemIds)
      e.presentation.description = getNotificationDescription(systemIds)
      e.presentation.icon = getNotificationIcon(systemIds)
    }
    e.presentation.isEnabled = notificationAware.isNotificationVisible()
  }

  @NlsActions.ActionText
  private fun getNotificationText(systemIds: Set<ProjectSystemId>): String {
    val systemsPresentation = ExternalSystemUtil.naturalJoinSystemIds(systemIds)
    return ExternalSystemBundle.message("external.system.reload.notification.action.reload.text", systemsPresentation)
  }

  @NlsActions.ActionDescription
  private fun getNotificationDescription(systemIds: Set<ProjectSystemId>): String {
    val systemsPresentation = ExternalSystemUtil.naturalJoinSystemIds(systemIds)
    val productName = ApplicationNamesInfo.getInstance().fullProductName
    return ExternalSystemBundle.message("external.system.reload.notification.action.reload.description", systemsPresentation, productName)
  }

  private fun getNotificationIcon(systemIds: Set<ProjectSystemId>): Icon {
    val systemId = systemIds.singleOrNull() ?: return AllIcons.Actions.BuildLoadChanges
    val iconProvider = ExternalSystemIconProvider.getExtension(systemId)
    return iconProvider.reloadIcon
  }

  init {
    val productName = ApplicationNamesInfo.getInstance().fullProductName
    templatePresentation.icon = AllIcons.Actions.BuildLoadChanges
    templatePresentation.text = ExternalSystemBundle.message("external.system.reload.notification.action.reload.text.empty")
    templatePresentation.description = ExternalSystemBundle.message("external.system.reload.notification.action.reload.description.empty", productName)
  }

  companion object {
    fun refreshProject(project: Project) {
      val projectNotificationAware = ExternalSystemProjectNotificationAware.getInstance(project)
      val systemIds = projectNotificationAware.getSystemIds()
      if (ExternalSystemUtil.confirmLoadingUntrustedProject(project, systemIds)) {
        val projectTracker = ExternalSystemProjectTracker.getInstance(project)
        projectTracker.scheduleProjectRefresh()
      }
    }
  }
}