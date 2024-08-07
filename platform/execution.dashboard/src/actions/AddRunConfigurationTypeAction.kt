// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
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

class AddRunConfigurationTypeAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val runDashboardManager = RunDashboardManager.getInstance(project)
    val addedTypes = runDashboardManager.types
    showAddPopup(project,
                 addedTypes,
                 { newTypes: List<ConfigurationType> ->
                   val updatedTypes: MutableSet<String> = HashSet(addedTypes)
                   for (type in newTypes) {
                     updatedTypes.add(type.id)
                   }
                   runDashboardManager.types = updatedTypes
                   if (RunManager.getInstance(project).allSettings.none { newTypes.contains(it.type) }) {
                     val type = newTypes.minWithOrNull(IGNORE_CASE_DISPLAY_NAME_COMPARATOR) ?: return@showAddPopup
                     addRunConfiguration(type, project)
                   }
                 },
                 { popup: JBPopup -> popup.showInBestPositionFor(e.dataContext) },
                 true)
  }

  private fun showAddPopup(project: Project, addedTypes: Set<String>,
                           onAddCallback: Consumer<List<ConfigurationType>>, popupOpener: Consumer<JBPopup>,
                           showApplicableTypesOnly: Boolean) {
    val allTypes = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.filter { !addedTypes.contains(it.id) }
    val popupList = getTypesPopupList(project, showApplicableTypesOnly, allTypes)

    val builder = JBPopupFactory.getInstance().createPopupChooserBuilder(popupList)
      .setTitle(ExecutionBundle.message("run.dashboard.configurable.add.configuration.type"))
      .setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
      .setRenderer(getTypesPopupRenderer())
      .setMovable(true)
      .setResizable(true)
      .setNamerForFiltering { if (it is ConfigurationType) it.displayName else null }
      .setAdText(ExecutionBundle.message("run.dashboard.configurable.types.panel.hint"))
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