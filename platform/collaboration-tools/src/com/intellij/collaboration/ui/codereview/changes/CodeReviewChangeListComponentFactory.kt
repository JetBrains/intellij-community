// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.changes

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.CodeReviewProgressTreeModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.setupCodeReviewProgressModel
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import javax.swing.event.TreeSelectionListener

object CodeReviewChangeListComponentFactory {

  fun createIn(cs: CoroutineScope, vm: CodeReviewChangeListViewModel,
               progressModel: CodeReviewProgressTreeModel<*>?,
               emptyTextText: @Nls String): AsyncChangesTree {
    val tree = CodeReviewChangesTreeFactory(vm.project, SingleValueModel(vm.changes))
      .create(emptyTextText, false).also { tree ->
        tree.doubleClickHandler = Processor { e ->
          if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, e)) return@Processor false
          vm.updateSelectedChangesFromTree(tree)
          vm.showDiffPreview()
          true
        }

        tree.enterKeyHandler = Processor {
          vm.updateSelectedChangesFromTree(tree)
          vm.showDiffPreview()
          true
        }
      }.apply {
        if (progressModel != null) {
          setupCodeReviewProgressModel(progressModel)
        }
      }

    cs.launch {
      // magic with selection to skip selection reset after update
      val selectionListener = TreeSelectionListener {
        vm.updateSelectedChangesFromTree(tree)
      }

      vm.selectionRequests.collectLatest {
        tree.removeTreeSelectionListener(selectionListener)
        when (it) {
          is CodeReviewChangeListViewModel.SelectionRequest.All -> {
            tree.invokeAfterRefresh {
              TreeUtil.selectFirstNode(tree)
              tree.addTreeSelectionListener(selectionListener)
            }
          }
          is CodeReviewChangeListViewModel.SelectionRequest.OneChange -> {
            tree.invokeAfterRefresh {
              tree.setSelectedChanges(listOf(it.change))
              tree.addTreeSelectionListener(selectionListener)
            }
          }
        }
      }
    }
    return tree
  }
}

private fun CodeReviewChangeListViewModel.updateSelectedChangesFromTree(tree: AsyncChangesTree) {
  var fuzzy = false
  val changes = mutableListOf<Change>()
  VcsTreeModelData.selected(tree).iterateRawNodes().forEach {
    if (it.isLeaf) {
      val change = it.userObject as? Change
      changes.add(change!!)
    }
    else {
      fuzzy = true
    }
  }
  val selection = if (changes.isEmpty()) null
  else if (fuzzy) {
    ChangesSelection.Fuzzy(changes)
  }
  else {
    ChangesSelection.Precise(this.changes, changes[0])
  }
  updateSelectedChanges(selection)
}