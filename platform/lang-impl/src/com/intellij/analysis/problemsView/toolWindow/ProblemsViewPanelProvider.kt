package com.intellij.analysis.problemsView.toolWindow

import com.intellij.openapi.extensions.ExtensionPointName
import org.jetbrains.annotations.ApiStatus

interface ProblemsViewPanelProvider {
  @ApiStatus.Internal
  companion object {
    @JvmStatic
    val EP: ExtensionPointName<ProblemsViewPanelProvider> = ExtensionPointName("com.intellij.problemsViewPanelProvider")
  }

  /**
   * @return Problem view tab or null, if was unable to create
   */
  fun create(): ProblemsViewTab?
}