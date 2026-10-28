// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.lvcs.impl.actions

import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.platform.lvcs.impl.settings.ActivityViewApplicationSettings
import com.intellij.platform.lvcs.impl.ui.ActivityViewDataKeys

internal class ShowAllLabelsAction: DumbAwareToggleAction() {

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun isSelected(e: AnActionEvent): Boolean {
    return isShowAllLabelsEnabled()
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    service<ActivityViewApplicationSettings>().showAllLabels = state
    e.getData(ActivityViewDataKeys.ACTIVITY_VIEW_MODEL)?.setAllLabelsShown(state)
  }
}

internal fun isShowAllLabelsEnabled(): Boolean =
  service<ActivityViewApplicationSettings>().showAllLabels
