// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.diff.impl.ui

import com.intellij.diff.DiffTool
import com.intellij.diff.FrameDiffTool
import com.intellij.diff.tools.combined.CombinedDiffTool
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.Presentation
import com.intellij.openapi.actionSystem.ex.CustomComponentAction
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.ui.dsl.builder.IntelliJSpacingConfiguration
import com.intellij.ui.dsl.builder.SegmentedButton
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent
import com.intellij.ui.dsl.builder.components.SegmentedButtonComponent.Companion.whenItemSelected
import org.jetbrains.annotations.ApiStatus
import javax.swing.JComponent

@ApiStatus.Experimental
@Suppress("DialogTitleCapitalization")
abstract class DiffToolChooser(private val project: Project?) : DumbAwareAction(), CustomComponentAction {
  private val segmentedButton = createComponent()

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

  override fun createCustomComponent(presentation: Presentation, place: String): JComponent {
    segmentedButton.items = getTools()
    segmentedButton.selectedItem = getActiveTool()
    segmentedButton.whenItemSelected { if (project != null) onSelected(project, it) }
    return segmentedButton
  }

  companion object {
    @JvmStatic
    @ApiStatus.Internal
    fun createComponent(): SegmentedButtonComponent<DiffTool> {
      return SegmentedButtonComponent { diffTool: DiffTool ->
        val icon = when (diffTool) {
          is FrameDiffTool -> diffTool.icon
          is CombinedDiffTool -> diffTool.getIcon()
          else -> null
        }
        if (icon != null)
          SegmentedButton.createPresentation(icon = icon, toolTipText = diffTool.name)
        else
          SegmentedButton.createPresentation(text = diffTool.name)
      }.apply {
        spacing = IntelliJSpacingConfiguration()
      }
    }
  }
}
