// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ex.ApplicationInfoEx
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.awt.event.ActionEvent
import java.util.concurrent.TimeUnit
import javax.swing.AbstractAction
import javax.swing.Action
import javax.swing.JComponent

internal class AnalyzePluginStartupPerformanceAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    PluginStartupCostDialog(project).show()
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = e.project != null
  }
}

data class PluginStartupCostEntry(
  val pluginId: String,
  val pluginName: String,
  val cost: Long,
  val costDetails: String
)

class PluginStartupCostDialog(private val project: Project) : DialogWrapper(project) {
  val pluginsToDisable = mutableSetOf<String>()
  lateinit var tableModel: ListTableModel<PluginStartupCostEntry>
  lateinit var table: TableView<PluginStartupCostEntry>

  init {
    title = "Startup Time Cost per Plugin"
    init()
  }

  override fun createCenterPanel(): JComponent {
    val pluginCostMap = StartUpPerformanceService.getInstance().pluginCostMap
    val tableData = pluginCostMap
      .mapNotNull { (pluginId, costMap) ->
        if (!ApplicationManager.getApplication().isInternal &&
            (ApplicationInfoEx.getInstanceEx()).isEssentialPlugin(pluginId)) {
          return@mapNotNull null
        }

        val name = PluginManagerCore.getPlugin(PluginId.getId(pluginId))?.name ?: return@mapNotNull null

        var totalCost = 0L
        costMap.forEachValue {
          totalCost += it
          true
        }

        val ids = costMap.keys()
        ids.sort()
        val costDetails = StringBuilder()
        for (id in ids) {
          costDetails.append(id).append(": ").append(TimeUnit.NANOSECONDS.toMillis(costMap[id as String]))
          costDetails.append('\n')
        }

        PluginStartupCostEntry(pluginId, name, totalCost, costDetails.toString())
      }
      .sortedByDescending { it.cost }

    val pluginColumn = object : ColumnInfo<PluginStartupCostEntry, String>("Plugin") {
      override fun valueOf(item: PluginStartupCostEntry) =
        item.pluginName + (if (item.pluginId in pluginsToDisable) " (will be disabled)" else "")
    }
    val costColumn = object : ColumnInfo<PluginStartupCostEntry, Int>("Startup Time (ms)") {
      override fun valueOf(item: PluginStartupCostEntry) = TimeUnit.NANOSECONDS.toMillis(item.cost).toInt()
    }
    val costDetailsColumn = object : ColumnInfo<PluginStartupCostEntry, String>("Cost Details") {
      override fun valueOf(item: PluginStartupCostEntry) = item.costDetails
    }

    val columns = if (ApplicationManager.getApplication().isInternal) {
      arrayOf(pluginColumn, costColumn, costDetailsColumn)
    }
    else {
      arrayOf(pluginColumn, costColumn)
    }

    tableModel = ListTableModel(columns, tableData)

    table = TableView(tableModel).apply {
      setShowColumns(true)
    }
    return JBScrollPane(table)
  }

  override fun createLeftSideActions(): Array<Action> {
    val disableAction = object : AbstractAction("Disable Selected Plugins") {
      override fun actionPerformed(e: ActionEvent?) {
        for (costEntry in table.selectedObjects) {
          pluginsToDisable.add(costEntry.pluginId)
        }
        tableModel.fireTableDataChanged()
      }
    }
    return arrayOf(disableAction)
  }

  override fun doOKAction() {
    super.doOKAction()
    if (pluginsToDisable.isNotEmpty()) {
      val plugins = pluginsToDisable.map { PluginManagerCore.getPlugin(PluginId.getId(it)) }.toSet()
      IdeErrorsDialog.confirmDisablePlugins(project, plugins)
    }
  }
}