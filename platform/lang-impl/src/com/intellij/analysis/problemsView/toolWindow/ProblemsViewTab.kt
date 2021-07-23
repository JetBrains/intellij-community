package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.NonNls

interface ProblemsViewTab {

  fun getName(count: Int): @NlsContexts.TabTitle String

  @NonNls
  fun getTabId(): String
}