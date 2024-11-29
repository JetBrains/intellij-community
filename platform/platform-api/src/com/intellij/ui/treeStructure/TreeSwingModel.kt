// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import com.intellij.openapi.components.service
import com.intellij.ui.tree.TreeVisitor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import java.util.EventObject
import javax.swing.event.TreeModelListener
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreeModel
import javax.swing.tree.TreePath

@ApiStatus.Experimental
fun TreeSwingModel(coroutineScope: CoroutineScope, viewModel: TreeViewModel): TreeSwingModel =
  (service<TreeSwingModelFactory>()).createTreeSwingModel(coroutineScope, viewModel)

@ApiStatus.Experimental
interface TreeSwingModel : TreeModel, TreeVisitor.LoadingAwareAcceptor, BgtAwareTreeModel {
  val viewModel: TreeViewModel
  var showLoadingNode: Boolean
  override fun getRoot(): TreeNodeViewModel?
  override fun getChild(parent: Any?, index: Int): TreeNodeViewModel?
}

@ApiStatus.Internal
interface TreeSwingModelFactory {
  fun createTreeSwingModel(coroutineScope: CoroutineScope, viewModel: TreeViewModel): TreeSwingModel
}

@ApiStatus.Experimental
interface TreeSwingModelListener : TreeModelListener {
  fun selectionChanged(event: TreeSwingModelSelectionEvent)
  fun scrollRequested(event: TreeSwingModelScrollEvent)
}

@ApiStatus.Experimental
class TreeSwingModelSelectionEvent(
  source: TreeSwingModel,
  val newSelection: Array<TreePath>,
) : EventObject(source) {
  override fun toString(): String {
    return "TreeSwingModelSelectionEvent(newSelection=${newSelection.contentToString()}) ${super.toString()}"
  }
}

@ApiStatus.Experimental
class TreeSwingModelScrollEvent(
  source: TreeSwingModel,
  val scrollTo: TreePath,
) : EventObject(source) {
  override fun toString(): String {
    return "TreeSwingModelScrollEvent(scrollTo=$scrollTo) ${super.toString()}"
  }
}
