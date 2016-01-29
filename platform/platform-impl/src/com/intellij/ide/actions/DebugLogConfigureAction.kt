/*
 * Copyright 2000-2016 JetBrains s.r.o.
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
package com.intellij.ide.actions

import com.intellij.diagnostic.DebugLogManager
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
import javax.swing.JComponent
import javax.swing.JTextArea

class DebugLogConfigureAction : DumbAwareAction() {
  override fun actionPerformed(e: AnActionEvent) {
    val project = e.project ?: ProjectManager.getInstance().defaultProject
    val logCustomizer = ApplicationManager.getApplication().getComponent(DebugLogManager::class.java)!!
    val dialog = DebugLogConfigureDialog(project, logCustomizer.getSavedCategories())
    if (dialog.showAndGet()) {
      val categories = dialog.getLogCategories()
      logCustomizer.applyCategories(categories)
      logCustomizer.saveCategories(categories)
    }
  }
}

private val TRACE_SUFFIX = ":trace"
private val ALL_POSSIBLE_SEPARATORS = "(\n|,|;)+".toRegex()

private class DebugLogConfigureDialog(project: Project, categories: List<Pair<String, DebugLogManager.DebugLogLevel>>) : DialogWrapper(project, false) {
  private val myTextArea: JTextArea

  init {
    myTextArea = JTextArea(10, 30)
    myTextArea.text = categories.joinToString("\n") {
      when (it.second) {
        DebugLogManager.DebugLogLevel.DEBUG -> it.first
        DebugLogManager.DebugLogLevel.TRACE -> "${it.first}$TRACE_SUFFIX"
      }
    }
    title = "Custom Debug Log Configuration"
    init()
  }

  override fun getDimensionServiceKey(): String? {
    return "#com.intellij.ide.actions.DebugLogConfigureAction"
  }

  override fun createNorthPanel(): JComponent? {
    return JBLabel(XmlStringUtil.wrapInHtml("Enable DEBUG level for log categories (one per line).<br>Append '$TRACE_SUFFIX' suffix to a category to enable TRACE level."))
  }

  override fun createCenterPanel() = ScrollPaneFactory.createScrollPane(myTextArea)

  override fun getPreferredFocusedComponent() = myTextArea

  fun getLogCategories() =
      myTextArea.text
          .split(ALL_POSSIBLE_SEPARATORS)
          .filter { !StringUtil.isEmptyOrSpaces(it) }
          .map { it.trim() }
          .map {
            if (it.endsWith(TRACE_SUFFIX, ignoreCase = true)) Pair(it.dropLast(TRACE_SUFFIX.length), DebugLogManager.DebugLogLevel.TRACE)
            else Pair(it, DebugLogManager.DebugLogLevel.DEBUG)
          }
}
