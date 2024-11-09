// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diagnostic

import com.intellij.ide.IdeBundle
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.TableView
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.JBDimension
import com.intellij.util.ui.ListTableModel
import java.awt.Dimension
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

  override fun getActionUpdateThread(): ActionUpdateThread {
    return ActionUpdateThread.BGT
  }
}

private data class PluginStartupCostEntry(
  val pluginId: String,
  val pluginName: String,
  val cost: Long,
  val costDetails: String
)

private class PluginStartupCostDialog(private val project: Project) : DialogWrapper(project) {
  val pluginsToDisable = mutableSetOf<String>()
  lateinit var tableModel: ListTableModel<PluginStartupCostEntry>
  lateinit var table: TableView<PluginStartupCostEntry>

  init {
    title = IdeBundle.message("analyze.plugin.title")
    init()
  }

  override fun createCenterPanel(): JComponent {
    val pluginCostMap = StartUpPerformanceService.getInstance().getPluginCostMap()
    val tableData = pluginCostMap.mapNotNull { (pluginId, costMap) ->
      if (!ApplicationManager.getApplication().isInternal &&
          (ApplicationInfo.getInstance()).isEssentialPlugin(pluginId)) {
        return@mapNotNull null
      }

      val name = PluginManagerCore.getPlugin(PluginId.getId(pluginId))?.name ?: return@mapNotNull null

      var totalCost = 0L
      val iterator = costMap.values.iterator()
      while (iterator.hasNext()) {
        totalCost += iterator.nextLong()
      }

      val ids = costMap.keys.toMutableList()
      ids.sort()
      val costDetails = StringBuilder()
      for (id in ids) {
        costDetails.append(id).append(": ").append(TimeUnit.NANOSECONDS.toMillis(costMap.getLong(id)))
        costDetails.append('\n')
      }

      PluginStartupCostEntry(pluginId, name, totalCost, costDetails.toString())
    }
      .sortedByDescending { it.cost }

    val pluginColumn = object : ColumnInfo<PluginStartupCostEntry, String>(IdeBundle.message("column.name.plugin")) {
      override fun valueOf(item: PluginStartupCostEntry) =
        item.pluginName + (if (item.pluginId in pluginsToDisable) " (will be disabled)" else "")
    }
    val costColumn = object : ColumnInfo<PluginStartupCostEntry, Int>(IdeBundle.message("column.name.startup.time.ms")) {
      override fun valueOf(item: PluginStartupCostEntry) = TimeUnit.NANOSECONDS.toMillis(item.cost).toInt()
    }
    val costDetailsColumn = object : ColumnInfo<PluginStartupCostEntry, String>(IdeBundle.message("column.name.cost.details")) {
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
    val disableAction = object : AbstractAction(IdeBundle.message("button.disable.selected.plugins")) {
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
    DisablePluginsDialog.confirmDisablePlugins(
      project,
      pluginsToDisable.asSequence()
        .map(PluginId::getId)
        .mapNotNull(PluginManagerCore::getPlugin)
        .toList(),
    )
  }

  override fun getPreferredFocusedComponent(): JComponent = table

  override fun getInitialSize(): Dimension = JBDimension(800, 600)

  override fun getDimensionServiceKey(): String = "AnalyzePluginStartupPerformanceAction.PluginStartupCostDialog"
}
