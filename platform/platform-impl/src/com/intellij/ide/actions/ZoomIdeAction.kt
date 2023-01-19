// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAware

abstract class ZoomIdeAction : AnAction(), DumbAware {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = !UISettings.getInstance().presentationMode
  }
}

class ZoomInIdeAction : ZoomIdeAction() {
  companion object {
    private const val MAX_SCALE = 5
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.presentation.isEnabled &&
                               IdeScaleTransformer.currentScale < MAX_SCALE
  }
  override fun actionPerformed(e: AnActionEvent) {
    IdeScaleTransformer.zoomIn()
  }
}

class ZoomOutIdeAction : ZoomIdeAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.presentation.isEnabled &&
                               IdeScaleTransformer.currentScale > IdeScaleTransformer.DEFAULT_SCALE
  }

  override fun actionPerformed(e: AnActionEvent) {
    IdeScaleTransformer.zoomOut()
  }
}

class ResetIdeScaleAction : ZoomIdeAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.presentation.isEnabled &&
                               IdeScaleTransformer.currentScale != IdeScaleTransformer.DEFAULT_SCALE
  }

  override fun actionPerformed(e: AnActionEvent) {
    IdeScaleTransformer.reset()
  }
}
