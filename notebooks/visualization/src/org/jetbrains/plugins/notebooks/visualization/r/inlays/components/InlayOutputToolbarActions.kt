/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.InlayOutput.Companion.getToolbarPaneOrNull

class ClearOutputAction private constructor() : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = getToolbarPaneOrNull(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    getToolbarPaneOrNull(e)?.inlayOutput?.doClearAction()
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.EDT

  companion object {
    const val ID = "org.jetbrains.plugins.notebooks.visualization.r.inlays.components.ClearOutputAction"
  }
}


internal class SaveOutputAction private constructor() : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getInlayOutput<InlayOutput.WithSaveAs>() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getInlayOutput<InlayOutput.WithSaveAs>()?.saveAs()
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.EDT

  companion object {
    const val ID = "org.jetbrains.plugins.notebooks.visualization.r.inlays.components.SaveOutputAction"
  }
}


class CopyImageToClipboardAction private constructor() : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = e.getInlayOutput<InlayOutput.WithCopyImageToClipboard>() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.getInlayOutput<InlayOutput.WithCopyImageToClipboard>()?.copyImageToClipboard()
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.EDT

  companion object {
    const val ID = "org.jetbrains.plugins.notebooks.visualization.r.inlays.components.CopyImageToClipboardAction"
  }
}


private inline fun <reified T> AnActionEvent.getInlayOutput(): T? =
  getToolbarPaneOrNull(this)?.inlayOutput as? T
