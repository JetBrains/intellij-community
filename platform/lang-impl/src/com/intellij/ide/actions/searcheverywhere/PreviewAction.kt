// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.SEHeaderActionListener.Companion.SE_HEADER_ACTION_TOPIC
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI.isPreviewEnabled
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbAwareToggleAction
import java.util.function.Supplier

class PreviewAction : DumbAwareToggleAction(Supplier { IdeBundle.message("search.everywhere.preview.action.text") },
                                            Supplier { IdeBundle.message("search.everywhere.preview.action.description") },
                                            ExpUiIcons.General.PreviewHorizontally) {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = isPreviewEnabled()
    super.update(e)
  }

  override fun isSelected(e: AnActionEvent) =
    PropertiesComponent.getInstance().isTrueValue(SearchEverywhereUI.PREVIEW_PROPERTY_KEY)

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    PropertiesComponent.getInstance().updateValue(SearchEverywhereUI.PREVIEW_PROPERTY_KEY, state)
    ApplicationManager.getApplication().messageBus.syncPublisher<SEHeaderActionListener>(SE_HEADER_ACTION_TOPIC)
      .performed(SEHeaderActionListener.SearchEverywhereActionEvent(e.presentation.text))
  }
}