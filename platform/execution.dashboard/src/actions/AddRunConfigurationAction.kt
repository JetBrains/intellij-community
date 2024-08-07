// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.util.containers.ContainerUtil
import java.util.function.Consumer
import javax.swing.ListSelectionModel

class AddRunConfigurationAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    showAddPopup(project,
                 RunDashboardManager.getInstance(project).types,
                 { newTypes: List<ConfigurationType> ->
                   val type = newTypes.firstOrNull() ?: return@showAddPopup
                   addRunConfiguration(type, project)
                 },
                 { popup: JBPopup -> popup.showInBestPositionFor(e.dataContext) },
                 true)
  }

  private fun showAddPopup(project: Project, addedTypes: Set<String>,
                           onAddCallback: Consumer<List<ConfigurationType>>, popupOpener: Consumer<JBPopup>,
                           showApplicableTypesOnly: Boolean) {
    val allTypes = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.filter { addedTypes.contains(it.id) }
    val popupList = getTypesPopupList(project, showApplicableTypesOnly, allTypes)

    val builder = JBPopupFactory.getInstance().createPopupChooserBuilder(popupList)
      .setTitle(ExecutionBundle.message("add.new.run.configuration.action2.name"))
      .setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
      .setRenderer(getTypesPopupRenderer())
      .setMovable(true)
      .setResizable(true)
      .setNamerForFiltering { if (it is ConfigurationType) it.displayName else null }
      .setItemsChosenCallback { selectedValues ->
        val value = ContainerUtil.getOnlyItem(selectedValues)
        if (value is String) {
          showAddPopup(project, addedTypes, onAddCallback, popupOpener, false)
          return@setItemsChosenCallback
        }

        onAddCallback.accept(selectedValues.filterIsInstance<ConfigurationType>())
      }
    popupOpener.accept(builder.createPopup())
  }
}