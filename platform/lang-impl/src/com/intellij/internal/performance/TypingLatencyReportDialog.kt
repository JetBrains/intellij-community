// Copyright 2000-2018 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal.performance

import com.intellij.execution.filters.Filter
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.execution.ui.ConsoleViewContentType
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.extensions.Extensions
import com.intellij.openapi.fileChooser.FileChooserFactory
import com.intellij.openapi.fileChooser.FileSaverDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.io.FileUtil
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.components.JBBox
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import com.intellij.unscramble.AnalyzeStacktraceUtil
import com.intellij.util.ui.JBUI
import com.intellij.util.ui.tree.TreeUtil
import java.awt.event.ActionEvent
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

class TypingLatencyReportAction : AnAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: return
    TypingLatencyReportDialog(project).show()
  }
}

class TypingLatencyReportDialog(
  private val project: Project,
  private val threadDumps: List<String> = emptyList()
) : DialogWrapper(project) {
  private var currentThreadDump = 0
  private lateinit var consoleView: ConsoleView
  private lateinit var prevThreadDumpButton: JButton
  private lateinit var nextThreadDumpButton: JButton

  init {
    init()
    title = "Typing Latency Report"
  }

  override fun createCenterPanel(): JComponent {
    val jbScrollPane = createReportTree()
    if (threadDumps.isEmpty()) {
      return jbScrollPane
    }
    return JBSplitter(true).apply {
      firstComponent = jbScrollPane
      secondComponent = createThreadDumpBrowser()
    }
  }

  private fun createReportTree(): JBScrollPane {
    val root = DefaultMutableTreeNode()
    for (row in latencyMap.values.sortedBy { it.fileType.name }) {
      val rowNode = DefaultMutableTreeNode(row)
      root.add(rowNode)
      for (actionLatencyRecord in row.actionLatencyRecords.entries.sortedByDescending { it.value.averageLatency }) {
        rowNode.add(DefaultMutableTreeNode(actionLatencyRecord.toPair()))
      }
    }
    val reportList = Tree(DefaultTreeModel(root))
    reportList.isRootVisible = false
    reportList.cellRenderer = object : ColoredTreeCellRenderer() {
      override fun customizeCellRenderer(tree: JTree,
                                         value: Any?,
                                         selected: Boolean,
                                         expanded: Boolean,
                                         leaf: Boolean,
                                         row: Int,
                                         hasFocus: Boolean) {
        if (value == null) return
        val obj = (value as DefaultMutableTreeNode).userObject
        if (obj is FileTypeLatencyRecord) {
          icon = obj.fileType.icon
          append(formatLatency(obj.fileType.name, obj.totalLatency))
        }
        else if (obj is Pair<*, *>) {
          val pair = obj as Pair<String, LatencyRecord>
          append(formatLatency(pair.first, pair.second))
        }
      }

    }
    TreeUtil.expandAll(reportList)
    return JBScrollPane(reportList)
  }

  private fun formatLatency(action: String, latencyRecord: LatencyRecord): String {
    return "$action - avg ${latencyRecord.averageLatency} ms, max ${latencyRecord.maxLatency} ms"
  }

  private fun createThreadDumpBrowser(): JComponent {
    val builder = TextConsoleBuilderFactory.getInstance().createBuilder(project)
    builder.filters(*Extensions.getExtensions<Filter>(AnalyzeStacktraceUtil.EP_NAME, project))
    consoleView = builder.console
    Disposer.register(disposable, consoleView)

    val buttonsPanel = JBBox.createHorizontalBox()
    prevThreadDumpButton = JButton("<<").apply {
      addActionListener {
        currentThreadDump--
        updateCurrentThreadDump()
      }
    }
    nextThreadDumpButton = JButton(">>").apply {
      addActionListener {
        currentThreadDump++
        updateCurrentThreadDump()
      }
    }
    buttonsPanel.add(prevThreadDumpButton)
    buttonsPanel.add(Box.createHorizontalGlue())
    buttonsPanel.add(nextThreadDumpButton)

    updateCurrentThreadDump()

    return JBUI.Panels.simplePanel().addToCenter(consoleView.component).addToBottom(buttonsPanel)
  }

  private fun updateCurrentThreadDump() {
    consoleView.clear()
    consoleView.print(threadDumps[currentThreadDump], ConsoleViewContentType.NORMAL_OUTPUT)
    consoleView.scrollTo(0)
    prevThreadDumpButton.isEnabled = currentThreadDump > 0
    nextThreadDumpButton.isEnabled = currentThreadDump < threadDumps.size - 1
  }

  private fun formatReportAsText(): String {
    return buildString {
      for (row in latencyMap.values.sortedBy { it.fileType.name }) {
        appendln(formatLatency(row.fileType.name, row.totalLatency))
        appendln("Actions:")
        for (actionLatencyRecord in row.actionLatencyRecords.entries.sortedByDescending { it.value.averageLatency }) {
          appendln("  ${formatLatency(actionLatencyRecord.key, actionLatencyRecord.value)}")
        }
        appendln()
        if (threadDumps.isNotEmpty()) {
          appendln("Thread dumps:")
          for (threadDump in threadDumps) {
            appendln(threadDump)
            appendln("-".repeat(40))
          }
        }
      }
    }
  }

  override fun createActions(): Array<Action> {
    return arrayOf(ExportToFileAction(), okAction)
  }

  private inner class ExportToFileAction : AbstractAction("Export to File") {
    override fun actionPerformed(e: ActionEvent) {
      val descriptor = FileSaverDescriptor("Export Typing Latency Report", "File name:", "txt")
      val dialog = FileChooserFactory.getInstance().createSaveFileDialog(descriptor, contentPane)
      val virtualFileWrapper = dialog.save(null, "typing-latency.txt") ?: return
      FileUtil.writeToFile(virtualFileWrapper.file, formatReportAsText())
    }
  }
}
