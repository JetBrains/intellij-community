// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.buildView.frontend

import com.intellij.build.BuildId
import com.intellij.build.BuildTreeConsoleView
import com.intellij.build.BuildViewId
import com.intellij.build.SuccessfulStepsToggleAction
import com.intellij.build.WarningsToggleAction
import com.intellij.ide.OccurenceNavigator
import com.intellij.ide.rpc.ComponentDirectTransferId
import com.intellij.ide.rpc.getComponent
import com.intellij.ide.util.PropertiesComponent
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.ToggleAction
import com.intellij.openapi.actionSystem.remoting.ActionRemoteBehaviorSpecification
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.platform.util.coroutines.childScope
import com.intellij.ui.OnePixelSplitter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import java.awt.BorderLayout
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

  internal companion object {
    val DATA_KEY = DataKey.create<FrontendBuildView>("FrontendBuildView")
  }

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
}

private fun restoreSavedFiltering(treeView: BuildTreeView) {
  treeView.showingWarnings = PropertiesComponent.getInstance().getBoolean(WarningsToggleAction.STATE_KEY,
                                                                          WarningsToggleAction.DEFAULT_STATE)
  treeView.showingSuccessful = PropertiesComponent.getInstance().getBoolean(SuccessfulStepsToggleAction.STATE_KEY,
                                                                            SuccessfulStepsToggleAction.DEFAULT_STATE)
}

internal abstract class FilteringToggleAction(
  private val stateKey: String,
  private val defaultState: Boolean,
  private val property: KMutableProperty1<BuildTreeView, Boolean>,
) : ToggleAction(), DumbAware, ActionRemoteBehaviorSpecification.Frontend {
  override fun getActionUpdateThread() = ActionUpdateThread.EDT

  private fun AnActionEvent.getTreeView(): BuildTreeView? = getData(FrontendBuildView.DATA_KEY)?.treeView

  override fun isSelected(e: AnActionEvent): Boolean {
    val presentation = e.presentation
    val treeView = e.getTreeView()
    if (treeView == null) {
      presentation.isEnabledAndVisible = false
      return false
    }
    presentation.isEnabledAndVisible = true
    return property.get(treeView)
  }

  override fun setSelected(e: AnActionEvent, state: Boolean) {
    val treeView = e.getTreeView() ?: return
    property.set(treeView, state)
    PropertiesComponent.getInstance().setValue(stateKey, state, defaultState)
  }
}

internal class BuildViewFilterWarningsAction : FilteringToggleAction(WarningsToggleAction.STATE_KEY,
                                                                     WarningsToggleAction.DEFAULT_STATE,
                                                                     BuildTreeView::showingWarnings)

internal class BuildViewFilterSuccessfulAction : FilteringToggleAction(SuccessfulStepsToggleAction.STATE_KEY,
                                                                       SuccessfulStepsToggleAction.DEFAULT_STATE,
                                                                       BuildTreeView::showingSuccessful)
