/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.PlatformCoreDataKeys
import com.intellij.openapi.project.DumbAwareAction

import org.jetbrains.plugins.notebooks.visualization.r.ui.DumbAwareActionAdapter

class ClearOutputAction : DumbAwareActionAdapter()

internal class SaveOutputAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val toolbarPane = getToolbarPaneOrNull(e)
    e.presentation.isEnabledAndVisible = toolbarPane != null
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.EDT

  override fun actionPerformed(e: AnActionEvent) {
    val toolbarPane = getToolbarPaneOrNull(e) ?: return
    toolbarPane.inlayOutput.saveAs()
  }

  private fun getToolbarPaneOrNull(e: AnActionEvent): ToolbarPane? =
    e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? ToolbarPane

  companion object {
    const val ID = "org.jetbrains.plugins.notebooks.visualization.r.inlays.components.SaveOutputAction"
  }
}

class CopyImageToClipboardAction : DumbAwareActionAdapter()
