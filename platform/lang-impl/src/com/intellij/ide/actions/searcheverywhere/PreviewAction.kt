// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.SEHeaderActionListener.Companion.SE_HEADER_ACTION_TOPIC
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI.isPreviewEnabled
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.PREVIEW_SWITCHED
import com.intellij.ide.ui.UISettings
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareToggleAction

internal const val PREVIEW_ACTION_ID = "Search.Everywhere.Preview"

internal class PreviewAction : DumbAwareToggleAction(IdeBundle.messagePointer("search.everywhere.preview.action.text"),
                                                     IdeBundle.messagePointer("search.everywhere.preview.action.description"),
                                                     AllIcons.General.PreviewHorizontally) {
  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isPreviewEnabled()
    super.update(e)
  }

  override fun isSelected(e: AnActionEvent): Boolean {
    return UISettings.getInstance().showPreviewInSearchEverywhere
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    PREVIEW_SWITCHED.log(e.project, state)
    toggleSearchPreview(state)
  }
}

private fun toggleSearchPreview(state: Boolean) {
  UISettings.getInstance().showPreviewInSearchEverywhere = state

  ApplicationManager.getApplication().messageBus.syncPublisher<SEHeaderActionListener>(SE_HEADER_ACTION_TOPIC)
    .performed(SEHeaderActionListener.SearchEverywhereActionEvent(PREVIEW_ACTION_ID))
}
