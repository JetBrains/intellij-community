package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.extensions.ExtensionPointName

interface ProblemsViewPanelProvider {
  companion object {
    @JvmStatic
    val EP = ExtensionPointName<ProblemsViewPanelProvider>("com.intellij.problemsViewPanelProvider")
  }

  fun create(): ProblemsViewTab?
}