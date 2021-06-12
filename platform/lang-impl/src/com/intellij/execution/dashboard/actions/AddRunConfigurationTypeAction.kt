// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.execution.dashboard.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.impl.RunConfigurable
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopup
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColoredListCellRenderer
import com.intellij.util.containers.ContainerUtil
import java.util.function.Consumer
import javax.swing.JList
import javax.swing.ListSelectionModel

class AddRunConfigurationTypeAction : DumbAwareAction() {

  private val IGNORE_CASE_DISPLAY_NAME_COMPARATOR = Comparator<ConfigurationType> { t1, t2 ->
    t1.displayName.compareTo(t2.displayName, ignoreCase = true)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    val runDashboardManager = RunDashboardManager.getInstance(project)
    val addedTypes = runDashboardManager.types
    showAddPopup(project, addedTypes,
                 { newTypes: List<ConfigurationType> ->
                   val updatedTypes: MutableSet<String> = HashSet(addedTypes)
                   for (type in newTypes) {
                     updatedTypes.add(type.id)
                   }
                   runDashboardManager.types = updatedTypes
                 }
   , { popup: JBPopup -> popup.showInBestPositionFor(e.dataContext) }, true)
  }

  private fun showAddPopup(project: Project, addedTypes: Set<String>,
                           onAddCallback: Consumer<List<ConfigurationType>>, popupOpener: Consumer<JBPopup>,
                           showApplicableTypesOnly: Boolean) {
    val allTypes = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList.filter { !addedTypes.contains(it.id) }
    val configurationTypes = RunConfigurable.getTypesToShow(project,
                                                            showApplicableTypesOnly && !project.isDefault,
                                                            allTypes).toMutableList()
    configurationTypes.sortWith(IGNORE_CASE_DISPLAY_NAME_COMPARATOR)
    val hiddenCount = allTypes.size - configurationTypes.size
    val popupList = ArrayList<Any>(configurationTypes)
    if (hiddenCount > 0) {
      popupList.add(ExecutionBundle.message("show.irrelevant.configurations.action.name", hiddenCount))
    }

    val builder = JBPopupFactory.getInstance().createPopupChooserBuilder(popupList)
      .setTitle(ExecutionBundle.message("run.dashboard.configurable.add.configuration.type"))
      .setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
      .setRenderer(object : ColoredListCellRenderer<Any>() {
        override fun customizeCellRenderer(list: JList<*>,
                                           value: Any,
                                           index: Int,
                                           selected: Boolean,
                                           hasFocus: Boolean) {
          if (value is ConfigurationType) {
            icon = value.icon
            append(value.displayName)
          }
          else {
            @NlsSafe val itemText = value.toString()
            append(itemText)
          }
        }
      })
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