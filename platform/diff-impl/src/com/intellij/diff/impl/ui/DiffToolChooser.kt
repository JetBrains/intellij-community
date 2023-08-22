// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl.ui

import com.intellij.diff.DiffTool
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.actionSystem.impl.ActionButtonWithText
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.dsl.builder.panel
import com.intellij.ui.dsl.gridLayout.UnscaledGaps
import com.intellij.util.ui.UIUtil
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
abstract class DiffToolChooser(private val project: Project?) : DumbAwareAction(), CustomComponentAction {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

  override fun update(e: AnActionEvent) {
    val presentation = e.presentation

    val activeTool = getActiveTool()
    presentation.text = activeTool.name

    if (getForcedDiffTool() != null) {
      presentation.isEnabledAndVisible = false
      return
    }

    for (tool in getTools()) {
      if (tool !== activeTool) {
        presentation.isEnabledAndVisible = true
        return
      }
    }

    presentation.isEnabledAndVisible = false
  }

  override fun actionPerformed(e: AnActionEvent) {
    //do nothing
  }

  abstract fun onSelected(project: Project, diffTool: DiffTool)

  abstract fun getTools(): List<DiffTool>
  abstract fun getActiveTool(): DiffTool
  abstract fun getForcedDiffTool(): DiffTool?

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    panel {
      row {
        segmentedButton(getTools()) { text = it.getName() }.apply {
          selectedItem = getActiveTool()
          whenItemSelected { if (project != null) onSelected(project, it) }
        }.customize(UnscaledGaps.EMPTY)
      }
    }.apply {
      // todo: fix segmented button
      UIUtil.forEachComponentInHierarchy(this) {
        if (it is ActionButtonWithText) {
          it.background = JBColor.lazy {
            UIUtil.getComboBoxDisabledBackground()
          }
        }
      }
    }
}
