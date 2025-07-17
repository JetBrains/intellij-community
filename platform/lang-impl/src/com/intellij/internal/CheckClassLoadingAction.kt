// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.plugins.PluginSetBuilder
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.contentModuleName
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import javax.swing.table.DefaultTableModel

internal class CheckClassLoadingAction : DumbAwareAction("Check Class Loading"), ActionRemoteBehaviorSpecification.Duplicated {
  override fun actionPerformed(e: AnActionEvent) {
    val className = Messages.showInputDialog(e.project, "Enter class name:", "Check Class Loading", Messages.getQuestionIcon())
    if (className.isNullOrBlank()) return
    
    val classLoadingMap = buildClassLoadingMap(className)

    val columns = arrayOf("Module", "Class Loader", "Loading")
    val data = classLoadingMap.map { (module, clazz) -> // keep topological order
      val classLoaderDesc = when (clazz) {
        null -> "null"
        else -> when (val cl = clazz.classLoader) {
          is PluginClassLoader -> (cl.pluginDescriptor as? PluginModuleDescriptor)?.let { " (${it.fullId})" } ?: ""
          else -> ""
        }
      }
      arrayOf(
        module.fullId,
        module.pluginClassLoader?.addressTag ?: "null",
        if (clazz != null) "Instance ${clazz.addressTag} loaded by ${clazz.classLoader.addressTag}$classLoaderDesc" else "null"
      )
    }.toTypedArray()

    val model = DefaultTableModel(columns, 0)
    model.addRow(columns) // FIXME idk how to enable header
    for (row in data) {
      model.addRow(row)
    }
    val table = object : JBTable(model) {
      override fun isCellEditable(row: Int, column: Int): Boolean = false
    }
    TableSpeedSearch.installOn(table)
    table.columnModel.getColumn(0).preferredWidth = 370
    table.columnModel.getColumn(1).preferredWidth = 90
    table.columnModel.getColumn(2).preferredWidth = 670

    val distinct = classLoadingMap.entries.groupBy { it.value }
      .mapValues { it.value.map { it.key } }
      .filter { it.key != null }
    val distinctCount = distinct.size

    val panel = panel {
      row("Class: $className") {}
      row {
        label(
          "$distinctCount instance${if (distinctCount == 1) "" else "s"} found. " +
          if (distinct.isNotEmpty()) "Representative module for each distinct instance:" else ""
        ).applyToComponent {
          icon = if (distinctCount != 1) Messages.getWarningIcon() else null
        }
      }
      row { text(distinct.values.joinToString("<br>") { it[0].fullId }) }
      row { label("Modules are in topological order.") }
      row { cell(table).align(Align.FILL) }
    }
    table.tableHeader.isVisible = true

    DialogBuilder(e.project).apply {
      setTitle("Class Loading Report")
      val scrollPane = JBScrollPane(panel)
      scrollPane.preferredSize = Dimension(1200, 650)
      setCenterPanel(scrollPane)
      addOkAction()
      show()
    }
  }

  private val PluginModuleDescriptor.fullId: String get() = buildString {
    append(pluginId)
    if (contentModuleName != null) append(":$contentModuleName")
  }

  private val Any.addressTag: String get() = "@" + System.identityHashCode(this).toString(16)

  private fun PluginModuleDescriptor.tryLoadClass(className: String): Class<*>? {
    if (pluginClassLoader == null) return null
    return try {
      Class.forName(className, false, pluginClassLoader)
    }
    catch (e: ClassNotFoundException) {
      null
    }
  }

  private fun buildClassLoadingMap(className: String): Map<PluginModuleDescriptor, Class<*>?> {
    val pluginSet = PluginManagerCore.getPluginSet()
    val loadingResults = mutableMapOf<PluginModuleDescriptor, Class<*>?>()
    val topologicalComparator = PluginSetBuilder(pluginSet.enabledPlugins.toSet()).topologicalComparator
    for (plugin in pluginSet.enabledPlugins) {
      loadingResults[plugin] = plugin.tryLoadClass(className)
      for (module in plugin.contentModules) {
        loadingResults[module] = module.tryLoadClass(className)
      }
    }
    return loadingResults.entries
      .sortedWith { (module1, _), (module2, _) ->
        val cl1 = module1.pluginClassLoader as? PluginClassLoader
        val cl2 = module2.pluginClassLoader as? PluginClassLoader
        topologicalComparator.compare(cl1?.pluginDescriptor as? PluginModuleDescriptor ?: module1,
                                      cl2?.pluginDescriptor as? PluginModuleDescriptor ?: module2)
      }
      .associateTo(LinkedHashMap()) { it.key to it.value }
  }
}