/*
 * Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
 */

package org.jetbrains.plugins.notebooks.visualization.r.inlays.components

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import org.jetbrains.plugins.notebooks.visualization.r.inlays.components.InlayOutput.Companion.getInlayOutput


class SaveOutputAction private constructor() : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = getInlayOutput<InlayOutput.WithSaveAs>(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    getInlayOutput<InlayOutput.WithSaveAs>(e)?.saveAs()
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.EDT

  companion object {
    const val ID = "org.jetbrains.plugins.notebooks.visualization.r.inlays.components.SaveOutputAction"
  }
}


class CopyImageToClipboardAction private constructor() : DumbAwareAction() {
  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = getInlayOutput<InlayOutput.WithCopyImageToClipboard>(e) != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    getInlayOutput<InlayOutput.WithCopyImageToClipboard>(e)?.copyImageToClipboard()
  }

  override fun getActionUpdateThread(): ActionUpdateThread =
    ActionUpdateThread.EDT

  companion object {
    const val ID = "org.jetbrains.plugins.notebooks.visualization.r.inlays.components.CopyImageToClipboardAction"
  }
}
