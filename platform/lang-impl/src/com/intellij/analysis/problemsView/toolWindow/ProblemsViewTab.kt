package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.util.NlsContexts
import com.intellij.ui.content.Content
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.NonNls
import java.util.concurrent.atomic.AtomicReference

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
abstract class ProblemsViewTabWithMetrics : SimpleToolWindowPanel(false), ProblemsViewTab {
  abstract val project: Project
  abstract val usagesTabId: String
  abstract val shownProblemsCount: Int

  private val shownTime: AtomicReference<Long> = AtomicReference()

  override fun selectionChangedTo(selected: Boolean) {
    super.selectionChangedTo(selected)
    visibilityChangedTo(selected)
  }

  override fun visibilityChangedTo(visible: Boolean) {
    super.visibilityChangedTo(visible)

    if (visible) {
      shownTime.set(System.nanoTime())
      ProblemsViewStatsCollector.tabShown(this)
    }
    else {
      val durationNano = shownTime.getAndSet(null)
      if (durationNano != null) {
        ProblemsViewStatsCollector.tabHidden(this, System.nanoTime() - durationNano)
      }
    }
  }
}