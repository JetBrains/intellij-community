// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.changes

import com.intellij.collaboration.ui.SingleValueModel
import com.intellij.collaboration.ui.codereview.CodeReviewProgressTreeModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.details.model.updateSelectedChangesFromTree
import com.intellij.collaboration.ui.codereview.setupCodeReviewProgressModel
import com.intellij.collaboration.util.isEqual
import com.intellij.openapi.vcs.changes.Change
import com.intellij.openapi.vcs.changes.ui.AsyncChangesTree
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
    val changesModel = SingleValueModel<List<Change>>(emptyList())
    val tree = CodeReviewChangesTreeFactory(vm.project, changesModel)
      .create(emptyTextText, false).also { tree ->
        tree.doubleClickHandler = Processor { e ->
          if (EditSourceOnDoubleClickHandler.isToggleEvent(tree, e)) return@Processor false
          vm.updateSelectedChangesFromTree(changesModel.value, tree)
          vm.showDiffPreview()
          true
        }

        tree.enterKeyHandler = Processor {
          vm.updateSelectedChangesFromTree(changesModel.value, tree)
          vm.showDiffPreview()
          true
        }
      }.apply {
        if (progressModel != null) {
          setupCodeReviewProgressModel(progressModel)
        }
      }

    cs.launch {
      // magic with selection to skip selection reset after model update
      val selectionListener = TreeSelectionListener {
        vm.updateSelectedChangesFromTree(changesModel.value, tree)
      }

      vm.updates.collectLatest {
        tree.removeTreeSelectionListener(selectionListener)
        if (!it.changes.isEqual(changesModel.value)) {
          changesModel.value = it.changes
        }
        when (it) {
          is CodeReviewChangeListViewModel.Update.WithSelectAll -> {
            tree.invokeAfterRefresh {
              TreeUtil.selectFirstNode(tree)
              tree.addTreeSelectionListener(selectionListener)
            }
          }
          is CodeReviewChangeListViewModel.Update.WithSelectChange -> {
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