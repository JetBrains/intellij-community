// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions

import com.intellij.ide.actions.ui.ideScaleIndicator.IdeScaleIndicatorManager
import com.intellij.ide.ui.UISettings
import com.intellij.ide.ui.UISettingsUtils
import com.intellij.ide.ui.percentValue
import com.intellij.openapi.actionSystem.ActionManager
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnAction
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.ex.ActionUtil
import com.intellij.openapi.project.DumbAware
import com.intellij.ui.UIBundle

abstract class ZoomIdeAction : AnAction(), DumbAware {
  protected val settingsUtils = UISettingsUtils.instance

  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  protected fun fireUISettingsChanged() {
    IdeScaleIndicatorManager.indicateIfChanged {
      UISettings.getInstance().fireUISettingsChanged()
    }
  }
}

class ZoomInIdeAction : ZoomIdeAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled = IdeScaleTransformer.Settings.increasedScale() != null
  }
  override fun actionPerformed(e: AnActionEvent) {
    IdeScaleTransformer.Settings.increasedScale()?.let {
      settingsUtils.setCurrentIdeScale(it)
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
      settingsUtils.setCurrentIdeScale(it)
      fireUISettingsChanged()
    }
  }
}

class ResetIdeScaleAction : ZoomIdeAction() {
  override fun update(e: AnActionEvent) {
    super.update(e)
    e.presentation.isEnabled =
      settingsUtils.currentIdeScale.percentValue != settingsUtils.currentDefaultScale.percentValue
  }

  override fun actionPerformed(e: AnActionEvent) {
    settingsUtils.setCurrentIdeScale(settingsUtils.currentDefaultScale)
    fireUISettingsChanged()
  }
}

class SwitchIdeScaleAction : AnAction(), DumbAware {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabled = true
    e.presentation.text = UIBundle.message("switch.ide.scale.action.format", UISettingsUtils.instance.currentIdeScale.percentValue)
  }

  override fun actionPerformed(e: AnActionEvent) {
    val changeScale = ActionManager.getInstance().getAction("ChangeIdeScale") ?: return
    ActionUtil.invokeAction(changeScale, e.dataContext, e.place, e.inputEvent, null)
  }
}
