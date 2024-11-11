// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import com.intellij.openapi.components.service
import com.intellij.ui.tree.TreeVisitor
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.ApiStatus
import javax.swing.event.TreeSelectionListener
import javax.swing.tree.TreeModel

@ApiStatus.Experimental
fun TreeSwingModel(coroutineScope: CoroutineScope, viewModel: TreeViewModel): TreeSwingModel =
  (service<TreeSwingModelFactory>()).createTreeSwingModel(coroutineScope, viewModel)

@ApiStatus.Experimental
interface TreeSwingModel : TreeModel, TreeVisitor.LoadingAwareAcceptor, BgtAwareTreeModel {
  val viewModel: TreeViewModel
  var showLoadingNode: Boolean
  fun addTreeSelectionListener(listener: TreeSelectionListener)
  fun removeTreeSelectionListener(listener: TreeSelectionListener)
  override fun getRoot(): TreeNodeViewModel?
  override fun getChild(parent: Any?, index: Int): TreeNodeViewModel?
}

@ApiStatus.Internal
interface TreeSwingModelFactory {
  fun createTreeSwingModel(coroutineScope: CoroutineScope, viewModel: TreeViewModel): TreeSwingModel
}
