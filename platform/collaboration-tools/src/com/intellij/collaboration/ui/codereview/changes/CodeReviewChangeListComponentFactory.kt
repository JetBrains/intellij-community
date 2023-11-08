// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.ui.codereview.changes

import com.intellij.collaboration.ui.codereview.CodeReviewProgressTreeModel
import com.intellij.collaboration.ui.codereview.details.model.CodeReviewChangeListViewModel
import com.intellij.collaboration.ui.codereview.setupCodeReviewProgressModel
import com.intellij.collaboration.util.ChangesSelection
import com.intellij.collaboration.util.RefComparisonChange
import com.intellij.collaboration.util.filePath
import com.intellij.collaboration.util.fileStatus
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vcs.FilePath
import com.intellij.openapi.vcs.changes.ChangesUtil
import com.intellij.openapi.vcs.changes.ui.*
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.getDataOrSuper
import com.intellij.openapi.vcs.changes.ui.VcsTreeModelData.selected
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ClientProperty
import com.intellij.ui.ExpandableItemsHandler
import com.intellij.ui.SelectionSaver
import com.intellij.util.EditSourceOnDoubleClickHandler
import com.intellij.util.Processor
import com.intellij.util.ui.tree.TreeUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.jetbrains.annotations.Nls
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.DefaultTreeModel

object CodeReviewChangeListComponentFactory {

  fun createIn(cs: CoroutineScope, vm: CodeReviewChangeListViewModel,
               progressModel: CodeReviewProgressTreeModel<*>?,
               emptyTextText: @Nls String): AsyncChangesTree {
    val treeModel = createTreeModel(vm)
    val tree = createTree(vm.project, treeModel).apply {
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
        setupCodeReviewProgressModel(progressModel)
      }
    }
    tree.rebuildTree()

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

  private fun createTree(project: Project, treeModel: AsyncChangesTreeModel) =
    object : AsyncChangesTree(project, false, false) {
      override val changesTreeModel: AsyncChangesTreeModel = treeModel

      override fun getData(dataId: String): Any? {
        return when {
          CommonDataKeys.NAVIGATABLE.`is`(dataId) -> getSelectedFiles().singleOrNull()?.let { OpenFileDescriptor(project, it) }
          CommonDataKeys.NAVIGATABLE_ARRAY.`is`(dataId) -> ChangesUtil.getNavigatableArray(project, getSelectedFiles())
          else -> return getDataOrSuper(project, this, dataId, super.getData(dataId))
        }
      }

      private fun getSelectedFiles(): List<VirtualFile> =
        selected(this)
          .userObjects(RefComparisonChange::class.java)
          .mapNotNull { changeNode -> changeNode.filePath.virtualFile }
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
  val changes = mutableListOf<RefComparisonChange>()
  selected(tree).iterateRawNodes().forEach {
    if (it.isLeaf) {
      val change = it.userObject as? RefComparisonChange
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