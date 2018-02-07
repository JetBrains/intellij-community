/*
 * Copyright 2000-2017 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.intellij.execution.impl

import com.intellij.execution.ExecutionBundle
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.ConfigurationType
import com.intellij.execution.dashboard.RunDashboardManager
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.impl.SimpleDataContext
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Comparing
import com.intellij.openapi.util.Conditions
import com.intellij.openapi.util.SystemInfo
import com.intellij.ui.*
import com.intellij.ui.components.JBList
import com.intellij.ui.speedSearch.SpeedSearchUtil
import com.intellij.util.containers.stream
import org.jetbrains.annotations.NonNls
import java.awt.BorderLayout
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.event.ListDataEvent
import javax.swing.event.ListDataListener

@NonNls private val EXPAND_PROPERTY_KEY = "ExpandRunDashboardTypesPanel"

private val IGNORE_CASE_DISPLAY_NAME_COMPARATOR = Comparator<ConfigurationType> { t1, t2 ->
  t1.displayName.compareTo(t2.displayName, ignoreCase = true)
}

/**
 * @author Konstantin Aleev
 */
internal class RunDashboardTypesPanel(private val myProject: Project) : JPanel(BorderLayout()) {
  private val listModel = CollectionListModel<ConfigurationType>()
  private val list = JBList<ConfigurationType>(listModel)

  init {
    val search = ListSpeedSearch(list, { it.displayName })
    search.comparator = SpeedSearchComparator(false)
    list.visibleRowCount = 5

    list.cellRenderer = object : ColoredListCellRenderer<ConfigurationType>() {
      override fun customizeCellRenderer(list: JList<out ConfigurationType>,
                                         value: ConfigurationType?,
                                         index: Int,
                                         selected: Boolean,
                                         hasFocus: Boolean) {
        if (value != null) {
          append(value.displayName)
          icon = value.icon
        }

        SpeedSearchUtil.applySpeedSearchHighlighting(list, this, true, selected)
      }
    }

    val toolbarDecorator = ToolbarDecorator.createDecorator(list)
    if (!SystemInfo.isMac) {
      toolbarDecorator.setAsUsualTopToolbar()
    }
    toolbarDecorator.setAddAction { showAddPopup(it, true) }
    toolbarDecorator.setRemoveAction { list.selectedValuesList.forEach { listModel.remove(it) } }
    toolbarDecorator.setMoveUpAction(null)
    toolbarDecorator.setMoveDownAction(null)

    val listPanel = JPanel(BorderLayout())
    listPanel.add(toolbarDecorator.createPanel(), BorderLayout.CENTER)

    val hideableDecorator = object : HideableDecorator(this,
                                                       ExecutionBundle.message("run.dashboard.configurable.types.panel.title"),
                                                       false) {
      override fun on() {
        super.on()
        storeState()
      }

      override fun off() {
        super.off()
        storeState()
      }

      private fun storeState() {
        PropertiesComponent.getInstance().setValue(EXPAND_PROPERTY_KEY, isExpanded.toString())
      }
    }
    hideableDecorator.setOn(PropertiesComponent.getInstance().getBoolean(EXPAND_PROPERTY_KEY, false))
    hideableDecorator.setContentComponent(listPanel)
  }

  private fun showAddPopup(button: AnActionButton, showApplicableTypesOnly: Boolean) {
    val allTypes = RunManager.getInstance(myProject).configurationFactoriesWithoutUnknown.filter { !listModel.contains(it) }
    val configurationTypes = getTypesToShow(showApplicableTypesOnly, allTypes).toMutableList()
    configurationTypes.sortWith(IGNORE_CASE_DISPLAY_NAME_COMPARATOR)
    val hiddenCount = allTypes.size - configurationTypes.size

    val actionGroup = DefaultActionGroup(null, false)
    configurationTypes.forEach({
                                 actionGroup.add(object : AnAction(it.displayName, null, it.icon) {
                                   override fun actionPerformed(e: AnActionEvent?) {
                                     listModel.add(it)
                                     listModel.sort(IGNORE_CASE_DISPLAY_NAME_COMPARATOR)
                                     list.selectedIndex = listModel.getElementIndex(it)
                                   }
                                 })
                               })
    if (hiddenCount > 0) {
      actionGroup.add(object : AnAction(ExecutionBundle.message("show.irrelevant.configurations.action.name", hiddenCount)) {
        override fun actionPerformed(e: AnActionEvent?) {
          showAddPopup(button, false)
        }
      })
    }

    val popup = JBPopupFactory.getInstance().createActionGroupPopup(
      ExecutionBundle.message("run.dashboard.configurable.add.configuration.type"),
      actionGroup,
      SimpleDataContext.getProjectContext(myProject),
      false,
      false,
      false,
      null,
      -1,
      Conditions.alwaysTrue<AnAction>())
    popup.show(button.preferredPopupPoint!!)
  }

  private fun getTypesToShow(showApplicableTypesOnly: Boolean, allTypes: List<ConfigurationType>): List<ConfigurationType> {
    if (showApplicableTypesOnly) {
      val applicableTypes = allTypes.filter { it.configurationFactories.stream().anyMatch { it.isApplicable(myProject) } }
      if (applicableTypes.size < (allTypes.size - 3)) {
        return applicableTypes
      }
    }
    return allTypes
  }

  fun addChangeListener(onChange: () -> Unit) {
    listModel.addListDataListener(object : ListDataListener {
      override fun contentsChanged(e: ListDataEvent?) {
        onChange()
      }

      override fun intervalRemoved(e: ListDataEvent?) {
        onChange()
      }

      override fun intervalAdded(e: ListDataEvent?) {
        onChange()
      }
    })
  }

  fun isModified() = !Comparing.equal(listModel.items.mapTo(HashSet(), { it.id }), RunDashboardManager.getInstance(myProject).types)

  fun reset() {
    listModel.removeAll()
    val types = RunDashboardManager.getInstance(myProject).types
    listModel.add(RunManagerImpl.getInstanceImpl(myProject).configurationFactoriesWithoutUnknown.filter { types.contains(it.id) })
    listModel.sort(IGNORE_CASE_DISPLAY_NAME_COMPARATOR)
  }

  fun apply() {
    val dashboardManager = RunDashboardManager.getInstance(myProject)
    val types = listModel.items.mapTo(HashSet(), { it.id })
    if (!Comparing.equal(types, dashboardManager.types)) {
      dashboardManager.types = types
    }
  }
}
