// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl.ui

import com.intellij.diff.DiffTool
import com.intellij.diff.FrameDiffTool
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.panel
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

  abstract fun getTools(): List<FrameDiffTool>
  abstract fun getActiveTool(): DiffTool
  abstract fun getForcedDiffTool(): DiffTool?

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent =
    panel {
      row {
        segmentedButton(getTools(), FrameDiffTool::getName).apply {
          (getActiveTool() as? FrameDiffTool)?.let {
            selectedItem = it
          }
          whenItemSelected { if (project != null) onSelected(project, it) }
        }
      }
    }
}
