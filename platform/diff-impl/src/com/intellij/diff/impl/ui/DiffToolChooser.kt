// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl.ui

import com.intellij.diff.DiffTool
import com.intellij.diff.FrameDiffTool
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DefaultActionGroup
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.dsl.builder.SpacingConfiguration
import com.intellij.ui.dsl.builder.components.SegmentedButtonToolbar
import javax.swing.JComponent

abstract class DiffToolChooser(private val targetComponent: JComponent? = null) : DumbAwareAction(), CustomComponentAction {

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

  abstract fun onSelected(e: AnActionEvent, diffTool: DiffTool)

  abstract fun getTools(): List<FrameDiffTool>
  abstract fun getActiveTool(): DiffTool
  abstract fun getForcedDiffTool(): DiffTool?

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    val group = DefaultActionGroup()
    for (tool in getTools()) {
      group.add(MyDiffToolAction(tool, tool == getActiveTool()))
    }
    return SegmentedButtonToolbar(group, SpacingConfiguration.createIntelliJSpacingConfiguration())
      .also { it.targetComponent = targetComponent }
  }

  private inner class MyDiffToolAction(private val diffTool: DiffTool, private val state: Boolean) :
    ToggleAction(diffTool.name), DumbAware {

    override fun isSelected(e: AnActionEvent): Boolean {
      return state
    }

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (getActiveTool() === diffTool) return

      onSelected(e, diffTool)
    }
  }
}
