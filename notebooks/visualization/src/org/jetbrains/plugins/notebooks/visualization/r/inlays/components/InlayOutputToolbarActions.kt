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

  override fun actionPerformed(e: AnActionEvent) {
    getToolbarPaneOrNull(e)?.inlayOutput?.saveAs()
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.EDT

  private fun getToolbarPaneOrNull(e: AnActionEvent): ToolbarPane? =
    e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? ToolbarPane

  companion object {
    const val ID = "org.jetbrains.plugins.notebooks.visualization.r.inlays.components.SaveOutputAction"
  }
}

class CopyImageToClipboardAction : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    val imageOutput = getImageOutputOrNull(e)
    e.presentation.isEnabledAndVisible = imageOutput != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    getImageOutputOrNull(e)?.copyImageToClipboard()
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.EDT

  private fun getImageOutputOrNull(e: AnActionEvent): CanCopyImageToClipboard? =
    (e.getData(PlatformCoreDataKeys.CONTEXT_COMPONENT) as? ToolbarPane)?.inlayOutput as? CanCopyImageToClipboard

  companion object {
    const val ID = "org.jetbrains.plugins.notebooks.visualization.r.inlays.components.CopyImageToClipboardAction"
  }
}

/** marker interface for [CopyImageToClipboardAction] */
interface CanCopyImageToClipboard {
  fun copyImageToClipboard()
}
