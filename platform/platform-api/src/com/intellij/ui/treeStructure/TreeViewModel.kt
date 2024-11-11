// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import com.intellij.openapi.components.service
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.TreeVisitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
fun TreeViewModel(coroutineScope: CoroutineScope, domainModel: TreeDomainModel): TreeViewModel =
  (service<TreeViewModelFactory>()).createTreeViewModel(coroutineScope, domainModel)

@ApiStatus.Experimental
interface TreeViewModel {
  val domainModel: TreeDomainModel
  val root: Flow<TreeNodeViewModel?>
  val selection: StateFlow<Set<TreeNodeViewModel>>
  fun invalidate(node: TreeNodeViewModel?, recursive: Boolean)
  fun setSelection(nodes: Collection<TreeNodeViewModel>)
  suspend fun accept(visitor: TreeViewModelVisitor, allowLoading: Boolean): TreeNodeViewModel?
  @ApiStatus.Internal
  suspend fun awaitUpdates()
}

@ApiStatus.Experimental
interface TreeViewModelVisitor {
  suspend fun visit(node: TreeNodeViewModel): TreeVisitor.Action
}

@ApiStatus.Experimental
interface TreeNodeViewModel {
  val parent: TreeNodeViewModel?
  val state: Flow<TreeNodeState>
  val children: Flow<List<TreeNodeViewModel>>
  fun stateSnapshot(): TreeNodeState
  fun setExpanded(isExpanded: Boolean)
  @ApiStatus.Internal
  fun getUserObject(): Any
}

@ApiStatus.Experimental
interface TreeNodeState {
  val presentation: TreeNodePresentation
  val isExpanded: Boolean
}

@ApiStatus.Internal
interface TreeViewModelFactory {
  fun createTreeViewModel(coroutineScope: CoroutineScope, domainModel: TreeDomainModel): TreeViewModel
}

@ApiStatus.Internal
data class TreeNodeStateImpl(
  override val presentation: TreeNodePresentationImpl,
  override val isExpanded: Boolean,
): TreeNodeState

@ApiStatus.Internal
data class TreeNodePresentationImpl(
  val isLeaf: Boolean,
  val icon: Icon?,
  val mainText: String,
  val fullText: List<TreeNodeTextFragment>,
  val toolTip: String?,
) : TreeNodePresentation

@ApiStatus.Internal
data class TreeNodeTextFragment(
  val text: String,
  val attributes: SimpleTextAttributes,
)
