// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.dvcs.push.ui

import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.changes.ui.AsyncChangesBrowserBase
import com.intellij.openapi.vcs.changes.ui.ChangesGroupingPolicyFactory
import com.intellij.openapi.vcs.changes.ui.SimpleAsyncChangesTreeModel
import com.intellij.openapi.vcs.changes.ui.TreeModelBuilder
import com.intellij.ui.components.JBLoadingPanel
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import javax.swing.tree.DefaultTreeModel

internal class PushLogChangesBrowser(project: Project,
                                     showCheckboxes: Boolean,
                                     highlightProblems: Boolean,
                                     private val loadingPane: JBLoadingPanel)
  : AsyncChangesBrowserBase(project, showCheckboxes, highlightProblems) {

  override val changesTreeModel: PushLogTreeModel = PushLogTreeModel(myProject)

  init {
    init()

    viewer.shouldShowBusyIconIfNeeded = false
    val edtContext = Dispatchers.EDT + ModalityState.any().asContextElement()
    viewer.scope.launch(edtContext) {
      viewer.busy.collectLatest { isLoading ->
        if (isLoading) {
          loadingPane.startLoading()
        }
        else {
          loadingPane.stopLoading()
        }
      }
    }
  }

  @RequiresEdt
  fun setCommitsToDisplay(newCommitNodes: List<CommitNode>) {
    changesTreeModel.commitNodes = newCommitNodes
    viewer.rebuildTree()
  }
}

internal class PushLogTreeModel(private val project: Project) : SimpleAsyncChangesTreeModel() {
  @Volatile
  var commitNodes: List<CommitNode> = emptyList()

  override fun buildTreeModelSync(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel {
    val currentChanges = PushLog.collectAllChanges(commitNodes)
    return TreeModelBuilder.buildFromChanges(project, grouping, currentChanges, null)
  }
}
