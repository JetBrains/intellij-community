// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.LocatableConfiguration
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.execution.impl.RunConfigurable
import com.intellij.execution.impl.RunDialog
import com.intellij.execution.impl.callNewConfigurationCreated
import com.intellij.openapi.actionSystem.ActionUpdateThread
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
import javax.swing.ListCellRenderer
import javax.swing.ListSelectionModel

private val IGNORE_CASE_DISPLAY_NAME_COMPARATOR = Comparator<ConfigurationType> { t1, t2 ->
  t1.displayName.compareTo(t2.displayName, ignoreCase = true)
}

class AddRunConfigurationAction : DumbAwareAction() {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.project != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    showAddPopup(project,
                 { performAdding(it, project) },
                 { popup -> popup.showInBestPositionFor(e.dataContext) },
                 true)
  }

  private fun performAdding(selectedTypes: List<ConfigurationType>, project: Project) {
    val runDashboardManager = RunDashboardManager.getInstance(project)
    val currentTypes = runDashboardManager.types

    val updatedTypes = HashSet(currentTypes)
    val addedTypes = ArrayList<ConfigurationType>()
    for (type in selectedTypes) {
      if (updatedTypes.add(type.id)) {
        addedTypes.add(type)
      }
    }

    if (!addedTypes.isEmpty()) {
      if (RunManager.getInstance(project).allSettings.none { addedTypes.contains(it.type) }) {
        val type = addedTypes.minWithOrNull(IGNORE_CASE_DISPLAY_NAME_COMPARATOR) ?: return
        if (!addRunConfiguration(type, project)) {
          return
        }
      }

      runDashboardManager.types = updatedTypes
    }
    else {
      val type = selectedTypes.minWithOrNull(IGNORE_CASE_DISPLAY_NAME_COMPARATOR) ?: return
      addRunConfiguration(type, project)
    }
  }

  private fun showAddPopup(project: Project,
                           onAddCallback: Consumer<List<ConfigurationType>>,
                           popupOpener: Consumer<JBPopup>,
                           showApplicableTypesOnly: Boolean) {
    val allTypes = ConfigurationType.CONFIGURATION_TYPE_EP.extensionList
    val popupList = getTypesPopupList(project, showApplicableTypesOnly, allTypes)

    val builder = JBPopupFactory.getInstance().createPopupChooserBuilder(popupList)
      .setTitle(ExecutionBundle.message("run.dashboard.configurable.add.configuration"))
      .setSelectionMode(ListSelectionModel.MULTIPLE_INTERVAL_SELECTION)
      .setRenderer(getTypesPopupRenderer())
      .setMovable(true)
      .setResizable(true)
      .setNamerForFiltering { if (it is ConfigurationType) it.displayName else null }
      .setAdText(ExecutionBundle.message("run.dashboard.configurable.types.panel.hint"))
      .setItemsChosenCallback { selectedValues ->
        val value = ContainerUtil.getOnlyItem(selectedValues)
        if (value is String) {
          showAddPopup(project, onAddCallback, popupOpener, false)
          return@setItemsChosenCallback
        }

        onAddCallback.accept(selectedValues.filterIsInstance<ConfigurationType>())
      }
    popupOpener.accept(builder.createPopup())
  }
}

private fun addRunConfiguration(type: ConfigurationType, project: Project): Boolean {
  val runManager = RunManager.getInstance(project)
  val settings = runManager.createConfiguration("", type.configurationFactories[0])

  val configuration = settings.configuration
  val suggestedName = (configuration as? LocatableConfiguration)?.suggestedName()?.takeIf { it.isNotEmpty()  }
  configuration.name = suggestedName ?: ExecutionBundle.message("run.configuration.unnamed.name.prefix")
  (configuration as? LocatableConfigurationBase<*>)?.setNameChangedByUser(false)
  runManager.setUniqueNameIfNeeded(settings)
  callNewConfigurationCreated(settings.getFactory(), settings.getConfiguration())

  val added = RunDialog.editConfiguration(project, settings,
                                          ExecutionBundle.message("add.new.run.configuration.action2.name"))
  if (added) {
    runManager.addConfiguration(settings)
  }
  return added
}

private fun getTypesPopupList(project: Project, showApplicableTypesOnly: Boolean, allTypes: List<ConfigurationType>): ArrayList<Any> {
  val configurationTypes = RunConfigurable.getTypesToShow(project,
                                                          showApplicableTypesOnly && !project.isDefault,
                                                          allTypes).toMutableList()
  configurationTypes.sortWith(IGNORE_CASE_DISPLAY_NAME_COMPARATOR)
  val hiddenCount = allTypes.size - configurationTypes.size
  val popupList = ArrayList<Any>(configurationTypes)
  if (hiddenCount > 0) {
    popupList.add(ExecutionBundle.message("show.irrelevant.configurations.action.name", hiddenCount))
  }
  return popupList
}

private fun getTypesPopupRenderer(): ListCellRenderer<Any> {
  return object : ColoredListCellRenderer<Any>() {
    override fun customizeCellRenderer(list: JList<*>, value: Any, index: Int, selected: Boolean, hasFocus: Boolean) {
      if (value is ConfigurationType) {
        icon = value.icon
        append(value.displayName)
      }
      else {
        @NlsSafe val itemText = value.toString()
        append(itemText)
      }
    }
  }
}