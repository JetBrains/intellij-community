// Copyright 2000-2021 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.actions

import com.intellij.featureStatistics.FeatureUsageTracker
import com.intellij.ide.actions.Switcher.SwitcherPanel
import com.intellij.ide.lightEdit.LightEditCompatible
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.DumbAwareToggleAction

internal class ShowRecentFilesAction : LightEditCompatible, SwitcherRecentFilesAction(false)
internal class ShowRecentlyEditedFilesAction : SwitcherRecentFilesAction(true)
internal abstract class SwitcherRecentFilesAction(val onlyEditedFiles: Boolean) : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    val project = event.project
    event.presentation.isEnabledAndVisible = project != null && Switcher.SWITCHER_KEY.get(project) == null
  }

  override fun actionPerformed(event: AnActionEvent) {
    val project = event.project ?: return
    FeatureUsageTracker.getInstance().triggerFeatureUsed("navigation.recent.files")
    SwitcherPanel(project, onlyEditedFiles)
  }
}


internal class SwitcherIterateThroughItemsAction : DumbAwareAction() {
  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = Switcher.SWITCHER_KEY.get(event.project) != null
  }

  override fun actionPerformed(event: AnActionEvent) {
    Switcher.SWITCHER_KEY.get(event.project)?.go(event.inputEvent)
  }
}


internal class SwitcherToggleOnlyEditedFilesAction : DumbAwareToggleAction() {
  private fun getCheckBox(event: AnActionEvent) =
    Switcher.SWITCHER_KEY.get(event.project)?.myShowOnlyEditedFilesCheckBox

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabledAndVisible = getCheckBox(event)?.isEnabled == true
  }

  override fun isSelected(event: AnActionEvent) = getCheckBox(event)?.isSelected ?: false
  override fun setSelected(event: AnActionEvent, selected: Boolean) {
    getCheckBox(event)?.isSelected = selected
  }
}
