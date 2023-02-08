// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.percentValue
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.UIBundle

abstract class ZoomIdeAction : AnAction(), DumbAware {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  protected fun fireUISettingsChanged() {
    UISettings.getInstance().fireUISettingsChanged()
  }
}

class ZoomInIdeAction : ZoomIdeAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = IdeScaleTransformer.Settings.increasedScale() != null
  }
  override fun actionPerformed(e: AnActionEvent) {
    IdeScaleTransformer.Settings.increasedScale()?.let {
      UISettingsUtils.setCurrentIdeScale(it)
      fireUISettingsChanged()
    }
  }
}

class ZoomOutIdeAction : ZoomIdeAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = IdeScaleTransformer.Settings.decreasedScale() != null
  }

  override fun actionPerformed(e: AnActionEvent) {
    IdeScaleTransformer.Settings.decreasedScale()?.let {
      UISettingsUtils.setCurrentIdeScale(it)
      fireUISettingsChanged()
    }
  }
}

class ResetIdeScaleAction : ZoomIdeAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled =
      UISettingsUtils.currentIdeScale.percentValue != UISettingsUtils.currentDefaultScale.percentValue
  }

  override fun actionPerformed(e: AnActionEvent) {
    UISettingsUtils.setCurrentIdeScale(UISettingsUtils.currentDefaultScale)
    fireUISettingsChanged()
  }
}

class CurrentIdeScaleAction : AnAction(), DumbAware {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = false
    e.presentation.text = UIBundle.message("current.scale.action.format", UISettingsUtils.currentIdeScale.percentValue)
  }

  override fun actionPerformed(e: AnActionEvent) {}
}

class ZoomIdeActionGroup : DefaultActionGroup(), AlwaysVisibleActionGroup
