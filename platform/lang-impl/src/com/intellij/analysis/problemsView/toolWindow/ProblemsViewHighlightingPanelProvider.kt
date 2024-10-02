package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class ProblemsViewHighlightingPanelProvider(private val project: Project) : ProblemsViewPanelProvider {
  override fun create(): ProblemsViewTab {
    return HighlightingPanel(project, ProblemsViewState.getInstance(project))
  }
}