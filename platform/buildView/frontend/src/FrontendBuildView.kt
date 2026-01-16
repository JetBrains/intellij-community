// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.build.*
import com.intellij.icons.AllIcons
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.ide.rpc.getComponent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.lang.LangBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.NlsContexts
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.OnePixelSplitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
import java.lang.ref.Reference
import java.lang.ref.WeakReference
import javax.swing.JPanel
import kotlin.reflect.KMutableProperty1

internal class FrontendBuildView(
  project: Project,
  parentScope: CoroutineScope,
  val buildId: BuildId,
  treeViewId: BuildViewId?,
  consoleComponent: ComponentDirectTransferId,
) : JPanel(BorderLayout()), Disposable {
  private val scope = parentScope.childScope("FrontendBuildView")
  internal val treeView: BuildTreeView?

  init {
    val console = consoleComponent.getComponent()
    if (treeViewId != null) {
      treeView = BuildTreeView(project, scope, treeViewId, false)
      restoreSavedFiltering(treeView)
      val splitter = OnePixelSplitter(BuildTreeConsoleView.SPLITTER_PROPERTY, BuildTreeConsoleView.SPLITTER_DEFAULT_PROPORTION).apply {
        firstComponent = treeView
        secondComponent = console
      }
      add(splitter)
    }
    else {
      treeView = null
      add(console)
    }
  }

  override fun dispose() {
    scope.cancel()
  }

  fun hasNextOccurence(): Boolean {
    return treeView?.hasNextOccurence() ?: false
  }

  fun hasPreviousOccurence(): Boolean {
    return treeView?.hasPreviousOccurence() ?: false
  }

  fun goNextOccurence(): OccurenceNavigator.OccurenceInfo? {
    return treeView?.goNextOccurence()
  }

  fun goPreviousOccurence(): OccurenceNavigator.OccurenceInfo? {
    return treeView?.goPreviousOccurence()
  }

  fun createFilteringActionGroup(): ActionGroup {
    val actionGroup = DefaultActionGroup(LangBundle.message("action.filters.text"), true)
    actionGroup.templatePresentation.icon = AllIcons.Actions.Show
    val treeViewRef = WeakReference(treeView)
    actionGroup.add(FilteringToggleAction(LangBundle.message("build.tree.filters.show.warnings"), WarningsToggleAction.STATE_KEY,
                                          BuildTreeView::showingWarnings, WarningsToggleAction.DEFAULT_STATE, treeViewRef))
    actionGroup.add(FilteringToggleAction(LangBundle.message("build.tree.filters.show.successful"), SuccessfulStepsToggleAction.STATE_KEY,
                                          BuildTreeView::showingSuccessful, SuccessfulStepsToggleAction.DEFAULT_STATE, treeViewRef))
    return actionGroup
  }
}

private fun restoreSavedFiltering(treeView: BuildTreeView) {
  treeView.showingWarnings = PropertiesComponent.getInstance().getBoolean(WarningsToggleAction.STATE_KEY,
                                                                          WarningsToggleAction.DEFAULT_STATE)
  treeView.showingSuccessful = PropertiesComponent.getInstance().getBoolean(SuccessfulStepsToggleAction.STATE_KEY,
                                                                            SuccessfulStepsToggleAction.DEFAULT_STATE)
}

private class FilteringToggleAction(
  @NlsContexts.Command text: String,
  private val stateKey: String,
  private val property: KMutableProperty1<BuildTreeView, Boolean>,
  private val defaultState: Boolean,
  private val treeViewRef: Reference<BuildTreeView?>,
) : ToggleAction(text), DumbAware {
  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  override fun isSelected(e: AnActionEvent): Boolean {
    val presentation = e.presentation
    val treeView = treeViewRef.get()
    if (treeView == null) {
      presentation.isEnabledAndVisible = false
      return false
    }
    presentation.isEnabledAndVisible = true
    return property.get(treeView)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val treeView = treeViewRef.get() ?: return
    property.set(treeView, state)
    PropertiesComponent.getInstance().setValue(stateKey, state, defaultState)
  }
}
