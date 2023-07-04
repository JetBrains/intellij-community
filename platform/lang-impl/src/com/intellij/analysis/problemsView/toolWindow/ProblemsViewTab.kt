package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls

interface ProblemsViewTab {
  @NlsContexts.TabTitle
  fun getName(count: Int): String

  @NonNls
  fun getTabId(): String

  fun orientationChangedTo(vertical: Boolean) {
  }

  fun selectionChangedTo(selected: Boolean) {
  }

  fun visibilityChangedTo(visible: Boolean) {
  }

  @ApiStatus.Internal
  fun customizeTabContent(content: Content) {
  }
}

@ApiStatus.Internal
object ProblemsViewTabUsages {
  fun logTabShown(project: Project, tabName: String, problemsCount: Int) {
    ProblemsViewStatsCollector.logTabShown(project, tabName, 0)
  }

  fun logTabHidden(project: Project, tabName: String, problemsCount: Int, durationNano: Long) {
    ProblemsViewStatsCollector.logTabHidden(project, tabName, 0, durationNano)
  }
}