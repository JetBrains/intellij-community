package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.extensions.ExtensionPointName

interface ProblemsViewPanelProvider {
  companion object {
    @JvmStatic
    val EP = ExtensionPointName<ProblemsViewPanelProvider>("com.intellij.problemsViewPanelProvider")
  }

  /**
   * @return Problem view tab or null, if was unable to create
   */
  fun create(): ProblemsViewTab?
}