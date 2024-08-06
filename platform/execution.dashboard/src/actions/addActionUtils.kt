// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.execution.dashboard.actions

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.configurations.LocatableConfiguration
import com.intellij.execution.configurations.LocatableConfigurationBase
import com.intellij.execution.impl.RunConfigurable
import com.intellij.execution.impl.RunDialog
import com.intellij.execution.impl.callNewConfigurationCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.ColoredListCellRenderer
import javax.swing.JList
import javax.swing.ListCellRenderer

internal val IGNORE_CASE_DISPLAY_NAME_COMPARATOR = Comparator<ConfigurationType> { t1, t2 ->
  t1.displayName.compareTo(t2.displayName, ignoreCase = true)
}

internal fun addRunConfiguration(type: ConfigurationType, project: Project) {
  val runManager = RunManager.getInstance(project)
  val settings = runManager.createConfiguration("", type.configurationFactories[0])

  val configuration = settings.configuration
  val suggestedName = (configuration as? LocatableConfiguration)?.suggestedName()?.takeIf { it.isNotEmpty()  }
  configuration.name = suggestedName ?: ExecutionBundle.message("run.configuration.unnamed.name.prefix")
  (configuration as? LocatableConfigurationBase<*>)?.setNameChangedByUser(false)
  runManager.setUniqueNameIfNeeded(settings)
  callNewConfigurationCreated(settings.getFactory(), settings.getConfiguration())

  if (RunDialog.editConfiguration(project, settings,
                                  ExecutionBundle.message("add.new.run.configuration.action2.name"))) {
    runManager.addConfiguration(settings)
  }
}

internal fun getTypesPopupList(project: Project, showApplicableTypesOnly: Boolean, allTypes: List<ConfigurationType>): ArrayList<Any> {
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

internal fun getTypesPopupRenderer(): ListCellRenderer<Any> {
  return object : ColoredListCellRenderer<Any>() {
    override fun customizeCellRenderer(
      list: JList<*>,
      value: Any,
      index: Int,
      selected: Boolean,
      hasFocus: Boolean,
    ) {
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