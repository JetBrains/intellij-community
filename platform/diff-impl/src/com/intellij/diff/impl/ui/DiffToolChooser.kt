// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.diff.impl.ui

import com.intellij.diff.DiffTool
import com.intellij.diff.FrameDiffTool
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.components.SegmentedButtonToolbar
import javax.swing.JComponent

@Suppress("DialogTitleCapitalization")
abstract class DiffToolChooser(private val targetComponent: JComponent? = null) : DumbAwareAction(), CustomComponentAction {

  private val actions = arrayListOf<MyDiffToolAction>()

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

  abstract fun onSelected(e: AnActionEvent, diffTool: DiffTool)

  abstract fun getTools(): List<FrameDiffTool>
  abstract fun getActiveTool(): DiffTool
  abstract fun getForcedDiffTool(): DiffTool?

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    actions.clear()

    for (tool in getTools()) {
      actions.add(MyDiffToolAction(tool, tool == getActiveTool()))
    }
    return SegmentedButtonToolbar(DefaultActionGroup(actions), IntelliJSpacingConfiguration())
      .also { it.targetComponent = targetComponent }
  }

  private inner class MyDiffToolAction(private val diffTool: DiffTool, private var state: Boolean) :
    ToggleAction(diffTool.name), DumbAware {

    override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.EDT

    override fun isSelected(e: AnActionEvent): Boolean = state

    override fun setSelected(e: AnActionEvent, state: Boolean) {
      if (getActiveTool() === diffTool) return

      actions.forEach { action -> action.state = !state }

      this.state = state

      onSelected(e, diffTool)
    }
  }
}
