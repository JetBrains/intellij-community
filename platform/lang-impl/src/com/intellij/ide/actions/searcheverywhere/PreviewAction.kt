// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.actions.searcheverywhere

import com.intellij.icons.ExpUiIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.actions.searcheverywhere.SEHeaderActionListener.Companion.SE_HEADER_ACTION_TOPIC
import com.intellij.ide.actions.searcheverywhere.SearchEverywhereUI.isPreviewEnabled
import com.intellij.ide.actions.searcheverywhere.statistics.SearchEverywhereUsageTriggerCollector.*
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.toolbar.floating.AbstractFloatingToolbarProvider
import com.intellij.openapi.editor.toolbar.floating.FloatingToolbarComponent
import com.intellij.openapi.project.DumbAwareToggleAction
import com.intellij.openapi.project.Project
import com.intellij.usages.impl.UsagePreviewPanel.Companion.PREVIEW_EDITOR_FLAG
import com.intellij.util.containers.DisposableWrapperList
import java.util.function.Supplier

const val PREVIEW_ACTION_ID = "Search.Everywhere.Preview"

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
    PREVIEW_SWITCHED.log(e.project, PREVIEW_STATE.with(state))
    togglePreview(state)
  }

  companion object {
    fun togglePreview(state: Boolean) {
      PropertiesComponent.getInstance().updateValue(SearchEverywhereUI.PREVIEW_PROPERTY_KEY, state)
      ApplicationManager.getApplication().messageBus.syncPublisher<SEHeaderActionListener>(SE_HEADER_ACTION_TOPIC)
        .performed(SEHeaderActionListener.SearchEverywhereActionEvent(PREVIEW_ACTION_ID))
    }
  }
}

class CloseSearchEverywherePreview : AnAction() {
  override fun getActionUpdateThread() = ActionUpdateThread.BGT

  override fun actionPerformed(e: AnActionEvent) {
    PREVIEW_CLOSED.log(e.project, PREVIEW_CLOSED_STATE.with(true))
    PreviewAction.togglePreview(false)
  }

  override fun update(e: AnActionEvent) {
    e.presentation.isEnabledAndVisible = PreviewExperiment.isExperimentEnabled
  }
}

class CloseSearchEverywherePreviewToolbar : AbstractFloatingToolbarProvider("Search.Everywhere.Preview.Close") {
  override val autoHideable = false
  private val toolbarComponents = DisposableWrapperList<Pair<Project, FloatingToolbarComponent>>()

  override fun isApplicable(dataContext: DataContext): Boolean {
    return PreviewExperiment.isExperimentEnabled && dataContext.getData(PlatformDataKeys.EDITOR)?.getUserData(PREVIEW_EDITOR_FLAG) != null
  }

  override fun register(dataContext: DataContext, component: FloatingToolbarComponent, parentDisposable: Disposable) {
    val project = dataContext.getData(CommonDataKeys.PROJECT) ?: return
    toolbarComponents.add(project to component, parentDisposable)
    component.scheduleShow()
  }
}