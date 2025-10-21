// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.changes

import com.intellij.collaboration.async.launchNow
import com.intellij.collaboration.ui.codereview.CodeReviewProgressTreeModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.setupCodeReviewProgressModel
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.fileStatus
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataKey
import com.intellij.openapi.actionSystem.DataSink
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.selected
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.SelectionSaver
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.Nls
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultTreeModel

object CodeReviewChangeListComponentFactory {
  val SELECTED_CHANGES = DataKey.create<List<RefComparisonChange>>("Code.Review.Change.List.Selected.RefComparisonChanges")

  fun createIn(cs: CoroutineScope, vm: CodeReviewChangeListViewModel,
               progressModel: CodeReviewProgressTreeModel<*>?,
               emptyTextText: @Nls String): AsyncChangesTree {
    val treeModel = createTreeModel(vm)
    val tree = cs.createTree(vm, treeModel).apply {
      emptyText.text = emptyTextText
    }.also { tree ->
      ClientProperty.put(tree, ExpandableItemsHandler.IGNORE_ITEM_SELECTION, true)
      SelectionSaver.installOn(tree)
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
        setupCodeReviewProgressModel(vm, progressModel)
      }
    }
    tree.rebuildTree()

    val selectionListener = TreeSelectionListener {
      vm.updateSelectedChangesFromTree(tree)
    }
    tree.addTreeSelectionListener(selectionListener)

    cs.launchNow {
      vm.selectionRequests.collect {
        tree.handleSelectionRequest(selectionListener, it)
      }
    }
    return tree
  }

  private fun AsyncChangesTree.handleSelectionRequest(
    selectionListener: TreeSelectionListener,
    request: CodeReviewChangeListViewModel.SelectionRequest,
  ) {
    // skip selection reset after update to avoid loop
    removeTreeSelectionListener(selectionListener)
    when (request) {
      is CodeReviewChangeListViewModel.SelectionRequest.All -> {
        invokeAfterRefresh {
          TreeUtil.selectFirstNode(this)
          addTreeSelectionListener(selectionListener)
        }
      }
      is CodeReviewChangeListViewModel.SelectionRequest.OneChange -> {
        invokeAfterRefresh {
          val currentSelection = selected(this).iterateUserObjects(RefComparisonChange::class.java)
          if (request.change !in currentSelection) {
            setSelectedChanges(listOf(request.change))
          }
          addTreeSelectionListener(selectionListener)
        }
      }
    }
  }

  private fun CoroutineScope.createTree(vm: CodeReviewChangeListViewModel, treeModel: AsyncChangesTreeModel) =
    object : AsyncChangesTree(vm.project, false, false) {
      override val changesTreeModel: AsyncChangesTreeModel = treeModel

      override fun uiDataSnapshot(sink: DataSink) {
        super.uiDataSnapshot(sink)
        VcsTreeModelData.uiDataSnapshot(sink, project, this)
        sink[CommonDataKeys.NAVIGATABLE] = getSelectedFiles().singleOrNull()?.let { OpenFileDescriptor(project, it) }
        sink[CommonDataKeys.NAVIGATABLE_ARRAY] = ChangesUtil.getNavigatableArray(project, getSelectedFiles())
        sink[SELECTED_CHANGES] = getSelectedChanges()
      }

      private fun getSelectedChanges(): List<RefComparisonChange> =
        selected(this)
          .userObjects(RefComparisonChange::class.java)

      private fun getSelectedFiles(): List<VirtualFile> =
        getSelectedChanges().mapNotNull { it.filePath.virtualFile }

      override fun installGroupingSupport(): ChangesGroupingSupport =
        if (vm is CodeReviewChangeListViewModel.WithGrouping) {
          ChangesGroupingSupport(vm.project, this, false).also { gs ->
            installGroupingSupport(this, gs, vm.grouping::value, vm::setGrouping)
            launchNow {
              vm.grouping.collect {
                if(gs.groupingKeys != it) {
                  gs.setGroupingKeysOrSkip(it)
                }
              }
            }
          }
        }
        else {
          super.installGroupingSupport()
        }
    }
}

private fun createTreeModel(vm: CodeReviewChangeListViewModel): AsyncChangesTreeModel {
  return object : AsyncChangesTreeModel {
    override suspend fun buildTreeModel(grouping: ChangesGroupingPolicyFactory): DefaultTreeModel =
      TreeModelBuilder(vm.project, grouping).apply {
        for (change in vm.changes) {
          insertChangeNode(change.filePath, myRoot, Node(change))
        }
      }.build()
  }
}

private class Node(change: RefComparisonChange) : AbstractChangesBrowserFilePathNode<RefComparisonChange>(change, change.fileStatus) {
  override fun filePath(userObject: RefComparisonChange): FilePath = userObject.filePath
  override fun originPath(userObject: RefComparisonChange): FilePath? = userObject.filePathBefore
}

private fun CodeReviewChangeListViewModel.updateSelectedChangesFromTree(tree: AsyncChangesTree) {
  var fuzzy = false
  val selectedChanges = mutableListOf<RefComparisonChange>()
  selected(tree).iterateRawNodes().forEach {
    if (it.isLeaf) {
      val change = it.userObject as? RefComparisonChange
      selectedChanges.add(change!!)
    }
    else {
      fuzzy = true
    }
  }
  val selection = when {
    selectedChanges.isEmpty() -> null
    fuzzy -> ChangesSelection.Fuzzy(selectedChanges)
    selectedChanges.size == 1 -> ChangesSelection.Precise(changes, selectedChanges[0])
    else -> ChangesSelection.Precise(selectedChanges, selectedChanges[0])
  }
  updateSelectedChanges(selection)
}