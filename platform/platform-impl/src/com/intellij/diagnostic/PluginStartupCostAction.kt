// Copyright 2000-2019 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diagnostic

import com.intellij.diagnostic.startUpPerformanceReporter.StartUpPerformanceReporter
import com.intellij.ide.plugins.PluginManager
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.StartupActivity
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.table.JBTable
import com.intellij.util.ui.ColumnInfo
import com.intellij.util.ui.ListTableModel
import java.util.concurrent.TimeUnit
import javax.swing.JComponent

internal class PluginStartupCostAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    PluginStartupCostDialog(project).show()
  }
}

data class PluginStartupCostEntry(val pluginName: String, val cost: Long, val costDetails: String)

class PluginStartupCostDialog(project: Project) : DialogWrapper(project) {
  init {
    title = "Startup Time Cost per Plugin"
    init()
  }

  override fun createCenterPanel(): JComponent {
    val pluginCostMap = StartupActivity.POST_STARTUP_ACTIVITY.findExtensionOrFail(
      StartUpPerformanceReporter::class.java).pluginCostMap!!
    val tableData = pluginCostMap
      .mapNotNull { (pluginId, costMap) ->
        val name = PluginManager.getPlugin(PluginId.getId(pluginId))?.name ?: return@mapNotNull null

        var totalCost = 0L
        costMap.forEachValue {
          totalCost += it
          true
        }

        @Suppress("UNCHECKED_CAST")
        val ids = costMap.keys()
        ids.sort()
        val costDetails = StringBuilder()
        for (id in ids) {
          costDetails.append(id).append(": ").append(TimeUnit.NANOSECONDS.toMillis(costMap[id as String]))
          costDetails.append('\n')
        }

        PluginStartupCostEntry(name, totalCost, costDetails.toString())
      }
      .sortedByDescending { it.cost }

    val model = ListTableModel<PluginStartupCostEntry>(
      arrayOf(
        object : ColumnInfo<PluginStartupCostEntry, String>("Plugin") {
          override fun valueOf(item: PluginStartupCostEntry) = item.pluginName
        },
        object : ColumnInfo<PluginStartupCostEntry, Int>("Cost (ms)") {
          override fun valueOf(item: PluginStartupCostEntry) = TimeUnit.NANOSECONDS.toMillis(item.cost).toInt()
        },
        object : ColumnInfo<PluginStartupCostEntry, String>("Cost Details") {
          override fun valueOf(item: PluginStartupCostEntry) = item.costDetails
        }
      ), tableData)

    val table = JBTable(model).apply {
      setShowColumns(true)
    }
    return JBScrollPane(table)
  }
}
