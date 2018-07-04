// Copyright 2000-2017 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.diagnostic.DebugLogManager
import com.intellij.diagnostic.DebugLogManager.DebugLogLevel
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.ui.DialogWrapper
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.ScrollPaneFactory
import com.intellij.ui.components.JBLabel
import com.intellij.xml.util.XmlStringUtil
import javax.swing.JTextArea

class DebugLogConfigureAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: ProjectManager.getInstance().defaultProject
    val logCustomizer = ApplicationManager.getApplication().getComponent(DebugLogManager::class.java)!!
    val currentCategories = logCustomizer.getSavedCategories()
    val dialog = DebugLogConfigureDialog(project, currentCategories)
    if (dialog.showAndGet()) {
      logCustomizer.clearCategories(currentCategories)
      val categories = dialog.getLogCategories()
      logCustomizer.applyCategories(categories)
      logCustomizer.saveCategories(categories)
    }
  }
}

private val TRACE_SUFFIX = ":trace"
private val ALL_POSSIBLE_SEPARATORS = "[\n,;]+".toRegex()

private class DebugLogConfigureDialog(project: Project, categories: List<Pair<String, DebugLogLevel>>) : DialogWrapper(project, false) {
  private val myTextArea: JTextArea

  init {
    myTextArea = JTextArea(10, 30)
    myTextArea.text = categories.joinToString("\n") {
      when (it.second) {
        DebugLogLevel.DEBUG -> it.first
        DebugLogLevel.TRACE -> "${it.first}$TRACE_SUFFIX"
      }
    }
    title = "Custom Debug Log Configuration"
    init()
  }

  override fun getDimensionServiceKey() = "#com.intellij.ide.actions.DebugLogConfigureAction"

  override fun createNorthPanel() = JBLabel(XmlStringUtil.wrapInHtml(
    "Enable DEBUG level for log categories (one per line).<br>Append '$TRACE_SUFFIX' suffix to a category to enable TRACE level."))

  override fun createCenterPanel() = ScrollPaneFactory.createScrollPane(myTextArea)

  override fun getPreferredFocusedComponent() = myTextArea

  fun getLogCategories() =
    myTextArea.text
      .split(ALL_POSSIBLE_SEPARATORS)
      .filter { !StringUtil.isEmptyOrSpaces(it) }
      .map { it.trim() }
      .map {
        if (it.endsWith(TRACE_SUFFIX, ignoreCase = true)) it.dropLast(TRACE_SUFFIX.length) to DebugLogLevel.TRACE
        else it to DebugLogLevel.DEBUG
      }
}