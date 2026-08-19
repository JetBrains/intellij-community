// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.internal

import com.intellij.ide.CopyProvider
import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.ide.plugins.PluginModuleDescriptor
import com.intellij.ide.plugins.cl.PluginClassLoader
import com.intellij.ide.plugins.contentModuleName
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.actionSystem.PlatformDataKeys
import com.intellij.openapi.actionSystem.UiDataProvider
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.ui.DialogBuilder
import com.intellij.openapi.ui.Messages
import com.intellij.ui.TableSpeedSearch
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.dsl.builder.Align
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.table.JBTable
import java.awt.Dimension
import java.awt.datatransfer.StringSelection
import javax.swing.JTable
import javax.swing.table.DefaultTableModel

internal class CheckClassLoadingAction : DumbAwareAction(), ActionRemoteBehaviorSpecification.Duplicated {
  override fun actionPerformed(e: AnActionEvent) {
    val className = Messages.showInputDialog(e.project, "Enter class name:", "Check Class Loading", Messages.getQuestionIcon())
    if (className.isNullOrBlank()) return

    val classLoadingMap = buildClassLoadingMap(className)

    val columns = arrayOf("Module", "Class Loader", "Loading Result")
    val data = classLoadingMap.map { (module, loadingResult) -> // keep topological order
      val clazz = loadingResult.getOrNull()
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
        loadingResult.fold(
          onSuccess = { if (clazz != null) "Instance ${clazz.addressTag} loaded by ${clazz.classLoader.addressTag}$classLoaderDesc" else "null" },
          onFailure = { it.message ?: it.toString() },
        )
      )
    }.toTypedArray()

    val model = DefaultTableModel(columns, 0)
    model.addRow(columns) // FIXME idk how to enable header
    for (row in data) {
      model.addRow(row)
    }
    val table = object : JBTable(model), UiDataProvider {
      private val copyProvider = TableCopyProvider(this)

      override fun isCellEditable(row: Int, column: Int): Boolean = false

      override fun uiDataSnapshot(sink: DataSink) {
        sink[PlatformDataKeys.COPY_PROVIDER] = copyProvider
      }
    }
    TableSpeedSearch.installOn(table)
    table.cellSelectionEnabled = true
    table.columnModel.getColumn(0).preferredWidth = 370
    table.columnModel.getColumn(1).preferredWidth = 90
    table.columnModel.getColumn(2).preferredWidth = 670

    val distinct = classLoadingMap.entries.groupBy { it.value.getOrNull() }
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

  private val PluginModuleDescriptor.fullId: String
    get() = buildString {
      append(pluginId)
      if (contentModuleName != null) append(":${contentModuleName}")
    }

  private val Any.addressTag: String get() = "@" + System.identityHashCode(this).toString(16)

  private fun PluginModuleDescriptor.tryLoadClass(className: String): Result<Class<*>?> {
    if (pluginClassLoader == null) return Result.success(null)
    return try {
      Result.success(Class.forName(className, false, pluginClassLoader))
    }
    catch (_: ClassNotFoundException) {
      Result.success(null)
    }
    catch (e: Throwable) {
      Result.failure(e)
    }
  }

  private fun buildClassLoadingMap(className: String): Map<PluginModuleDescriptor, Result<Class<*>?>> {
    val loadingResults = mutableMapOf<PluginModuleDescriptor, Result<Class<*>?>>()
    val pluginSet = PluginManagerCore.getPluginSet().resolvedPluginSet
    for (moduleGroup in pluginSet.runtimeModuleGroupGraph.sortedGroups) {
      for (module in moduleGroup.sortedDescriptors) {
        if (module !is PluginModuleDescriptor) continue
        loadingResults[module] = module.tryLoadClass(className)
      }
    }
    return loadingResults
  }

  private class TableCopyProvider(private val table: JTable) : CopyProvider {
    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun performCopy(dataContext: DataContext) {
      val text = table.selectedRows.joinToString("\n") { row ->
        table.selectedColumns.joinToString("\t") { column ->
          table.getValueAt(row, column)?.toString().orEmpty()
        }
      }
      CopyPasteManager.getInstance().setContents(StringSelection(text))
    }

    override fun isCopyEnabled(dataContext: DataContext): Boolean =
      table.selectedRowCount > 0 && table.selectedColumnCount > 0

    override fun isCopyVisible(dataContext: DataContext): Boolean = true
  }
}
