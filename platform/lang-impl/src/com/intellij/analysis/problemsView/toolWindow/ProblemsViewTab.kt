package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.util.NlsContexts
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

  fun isCloseable(): Boolean = false
}