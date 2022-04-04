// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.progress.EmptyProgressIndicator
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.ChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.concurrency.annotations.RequiresEdt
import javax.swing.tree.DefaultTreeModel

class PushLogChangesBrowser(project: Project,
                            showCheckboxes: Boolean,
                            highlightProblems: Boolean,
                            private val loadingPane: JBLoadingPanel)
  : ChangesBrowserBase(project, showCheckboxes, highlightProblems), Disposable {
  private val executor = AppExecutorUtil.createBoundedScheduledExecutorService("PushLogChangesBrowser Pool", 1)
  private var indicator: ProgressIndicator? = null

  private var currentChanges: List<Change> = emptyList()

  init {
    init()
  }

  override fun dispose() {
    indicator?.cancel()
    indicator = null

    executor.shutdown()
  }

  override fun buildTreeModel(): DefaultTreeModel {
    return buildTreeModel(currentChanges)
  }

  private fun buildTreeModel(changes: List<Change>): DefaultTreeModel {
    return TreeModelBuilder.buildFromChanges(myProject, grouping, changes, null)
  }

  @RequiresEdt
  fun setCommitsToDisplay(commitNodes: List<CommitNode>) {
    val taskIndicator = initIndicator()
    executor.execute {
      ProgressManager.getInstance().executeProcessUnderProgress({ loadChanges(commitNodes, taskIndicator) }, taskIndicator)
    }
  }

  private fun loadChanges(commitNodes: List<CommitNode>, taskIndicator: ProgressIndicator) {
    taskIndicator.checkCanceled()
    val changes = PushLog.collectAllChanges(commitNodes)

    taskIndicator.checkCanceled()
    val treeModel = buildTreeModel(changes)

    invokeLater(ModalityState.stateForComponent(this)) {
      taskIndicator.checkCanceled()
      resetIndicator(taskIndicator)

      currentChanges = changes
      (myViewer as ChangesBrowserTreeList).updateTreeModel(treeModel)
    }
  }

  @RequiresEdt
  private fun initIndicator(): ProgressIndicator {
    val taskIndicator = EmptyProgressIndicator()
    indicator?.cancel()
    indicator = taskIndicator
    loadingPane.startLoading()
    return taskIndicator
  }

  @RequiresEdt
  private fun resetIndicator(taskIndicator: ProgressIndicator) {
    if (indicator === taskIndicator) {
      indicator = null
      loadingPane.stopLoading()
    }
  }
}