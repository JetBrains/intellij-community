// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.actions.ui.ideScaleIndicator.IdeScaleIndicatorManager
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.UIBundle
import kotlin.math.roundToInt

abstract class ZoomIdeAction : AnAction(), DumbAware {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = !UISettings.getInstance().presentationMode
  }

  override fun actionPerformed(e: AnActionEvent) {
    e.project?.let { IdeScaleIndicatorManager.setup(it) }
  }
}

class ZoomInIdeAction : ZoomIdeAction() {
  companion object {
    private const val MAX_SCALE = 5
  }

  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.presentation.isEnabled &&
                               IdeScaleTransformer.instance.currentScale < MAX_SCALE
  }
  override fun actionPerformed(e: AnActionEvent) {
    super.actionPerformed(e)
    IdeScaleTransformer.instance.zoomIn()
  }
}

class ZoomOutIdeAction : ZoomIdeAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.presentation.isEnabled &&
                               IdeScaleTransformer.instance.currentScale > IdeScaleTransformer.DEFAULT_SCALE
  }

  override fun actionPerformed(e: AnActionEvent) {
    super.actionPerformed(e)
    IdeScaleTransformer.instance.zoomOut()
  }
}

class ResetIdeScaleAction : ZoomIdeAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = e.presentation.isEnabled &&
                               IdeScaleTransformer.instance.currentScale != IdeScaleTransformer.DEFAULT_SCALE
  }

  override fun actionPerformed(e: AnActionEvent) {
    super.actionPerformed(e)
    IdeScaleTransformer.instance.reset()
  }
}

class CurrentIdeScaleAction : AnAction(), DumbAware {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = false
    e.presentation.text = UIBundle.message("current.scale.action.format", scalePercentage)
  }

  override fun actionPerformed(e: AnActionEvent) {}

  companion object {
    val scalePercentage: Int
      get() = (IdeScaleTransformer.instance.currentScale * 100).roundToInt()
  }
}

class ZoomIdeActionGroup : DefaultActionGroup(), AlwaysVisibleActionGroup
