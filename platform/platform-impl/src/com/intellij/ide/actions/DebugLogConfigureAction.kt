// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.diagnostic.logs.DebugLogLevel
import com.intellij.diagnostic.logs.LogCategory
import com.intellij.diagnostic.logs.LogLevelConfigurationManager
import com.intellij.diagnostic.logs.LogLevelConfigurationManager.State
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
    val currentState = logLevelConfigurationManager.state

    val dialog = DebugLogConfigureDialog(project, currentState)
    if (dialog.showAndGet()) {
      val dialogState = dialog.getLogCategories()
      logLevelConfigurationManager.setCategories(dialogState)
    }
  }

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT
}

private const val TRACE_TOKEN = "trace"
private const val ALL_TOKEN = "all"
private const val SEPARATE_FILE_TOKEN = "separate"
private val ALL_POSSIBLE_SEPARATORS = "[\n,;]+".toRegex()

private class DebugLogConfigureDialog(project: Project, state: State) : DialogWrapper(project, false) {
  private val myTextArea = JTextArea(10, 30)

  init {
    myTextArea.margin = JBUI.insets(2)
    myTextArea.text = state.categories.joinToString("\n") {
      var line = when (it.level) {
        DebugLogLevel.DEBUG -> it.category
        DebugLogLevel.TRACE -> "${it.category}:$TRACE_TOKEN"
        DebugLogLevel.ALL -> "${it.category}:$ALL_TOKEN"
      }
      if (it.category in state.categoriesWithSeparateFiles) {
        line += ":$SEPARATE_FILE_TOKEN"
      }
      line
    }
    title = IdeBundle.message("dialog.title.custom.debug.log.configuration")
    init()
  }

  override fun getDimensionServiceKey() = "#com.intellij.ide.actions.DebugLogConfigureAction"

  override fun createNorthPanel() = JBLabel(
    XmlStringUtil.wrapInHtml(IdeBundle.message("label.enable.debug.level", ":$TRACE_TOKEN", ":$ALL_TOKEN", ":$SEPARATE_FILE_TOKEN")))

  override fun createCenterPanel() = ScrollPaneFactory.createScrollPane(myTextArea)

  override fun getPreferredFocusedComponent() = myTextArea

  fun getLogCategories(): State {
    val logCategories = mutableListOf<LogCategory>()
    val categoriesWithDedicatedFiles = hashSetOf<String>()
    for (line in myTextArea.text.split(ALL_POSSIBLE_SEPARATORS)) {
      val line = line.trim()
      if (line.isNotEmpty()) {
        val split = line.split(':')
        var level = DebugLogLevel.DEBUG
        val category = split[0].trim()
        for (token in split.drop(1)) {
          when (token.lowercase().trim()) {
            TRACE_TOKEN -> level = DebugLogLevel.TRACE
            ALL_TOKEN -> level = DebugLogLevel.ALL
            SEPARATE_FILE_TOKEN -> categoriesWithDedicatedFiles.add(category)
          }
        }
        logCategories.add(LogCategory(category, level))
      }
    }
    return State(logCategories, categoriesWithDedicatedFiles)
  }
}