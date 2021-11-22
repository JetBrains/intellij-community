package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.util.NlsContexts
import org.jetbrains.annotations.NonNls

interface ProblemsViewTab {

  @NlsContexts.TabTitle
  fun getName(count: Int): String

  @NonNls
  fun getTabId(): String
}