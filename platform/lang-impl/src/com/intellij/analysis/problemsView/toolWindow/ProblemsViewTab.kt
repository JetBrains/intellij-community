package com.intellij.analysis.problemsView.toolWindow

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