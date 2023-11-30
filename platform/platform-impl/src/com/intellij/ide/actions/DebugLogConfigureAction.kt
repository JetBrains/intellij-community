// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.ide.IdeBundle
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import javax.swing.JTextArea

internal class DebugLogConfigureAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: ProjectManager.getInstance().defaultProject
    val logLevelConfigurationManager = LogLevelConfigurationManager.getInstance()
    val currentCategories = logLevelConfigurationManager.getCategories()

    val dialog = DebugLogConfigureDialog(project, currentCategories)
    if (dialog.showAndGet()) {
      val dialogCategories = dialog.getLogCategories()
      logLevelConfigurationManager.setCategories(dialogCategories)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private const val TRACE_SUFFIX = ":trace"
private const val ALL_SUFFIX = ":all"
private val ALL_POSSIBLE_SEPARATORS = "[\n,;]+".toRegex()

private class DebugLogConfigureDialog(project: Project, categories: List<LogCategory>) : DialogWrapper(project, false) {
  private val myTextArea = JTextArea(10, 30)

  init {
    myTextArea.margin = JBUI.insets(2)
    myTextArea.text = categories.joinToString("\n") {
      when (it.level) {
        DebugLogLevel.DEBUG -> it.category
        DebugLogLevel.TRACE -> "${it.category}$TRACE_SUFFIX"
        DebugLogLevel.ALL -> "${it.category}$ALL_SUFFIX"
      }
    }
    title = IdeBundle.message("dialog.title.custom.debug.log.configuration")
    init()
  }

  override fun getDimensionServiceKey() = "#com.intellij.ide.actions.DebugLogConfigureAction"

  override fun createNorthPanel() = JBLabel(
    XmlStringUtil.wrapInHtml(IdeBundle.message("label.enable.debug.level", TRACE_SUFFIX, ALL_SUFFIX)))

  override fun createCenterPanel() = ScrollPaneFactory.createScrollPane(myTextArea)

  override fun getPreferredFocusedComponent() = myTextArea

  fun getLogCategories(): List<LogCategory> {
    return myTextArea.text
      .split(ALL_POSSIBLE_SEPARATORS)
      .asSequence()
      .filter { it.isNotBlank() }
      .map { it.trim() }
      .map {
        when {
          it.endsWith(TRACE_SUFFIX, ignoreCase = true) -> LogCategory(it.dropLast(TRACE_SUFFIX.length), DebugLogLevel.TRACE)
          it.endsWith(ALL_SUFFIX, ignoreCase = true) -> LogCategory(it.dropLast(ALL_SUFFIX.length), DebugLogLevel.ALL)
          else -> LogCategory(it, DebugLogLevel.DEBUG)
        }
      }
      .toList()
  }
}