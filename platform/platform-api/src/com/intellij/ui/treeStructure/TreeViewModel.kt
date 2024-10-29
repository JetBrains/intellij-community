// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import com.intellij.openapi.components.service
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.SuspendingTreeVisitor
import com.intellij.ui.tree.TreeVisitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon
import javax.swing.tree.TreePath

@ApiStatus.Experimental
fun TreeViewModel(coroutineScope: CoroutineScope, domainModel: TreeDomainModel): TreeViewModel =
  (service<TreeViewModelFactory>()).createTreeViewModel(coroutineScope, domainModel)

@ApiStatus.Experimental
interface TreeViewModel {
  val domainModel: TreeDomainModel
  val root: Flow<TreeNodeViewModel?>
  suspend fun invalidate(path: TreePath?, recursive: Boolean)
  var comparator: Comparator<in TreeNodeViewModel>?
  suspend fun accept(visitor: TreeVisitor, allowLoading: Boolean): TreePath?
  suspend fun accept(visitor: SuspendingTreeVisitor, allowLoading: Boolean): TreePath?
}

@ApiStatus.Experimental
interface TreeNodeViewModel {
  fun getUserObject(): Any
  val presentation: Flow<TreeNodePresentation>
  val children: Flow<List<TreeNodeViewModel>>
  fun presentationSnapshot(): TreeNodePresentation
}

@ApiStatus.Internal
interface TreeViewModelFactory {
  fun createTreeViewModel(coroutineScope: CoroutineScope, domainModel: TreeDomainModel): TreeViewModel
}

@ApiStatus.Internal
data class TreeNodePresentationImpl(
  override val isLeaf: Boolean,
  override val icon: Icon?,
  override val mainText: String,
  override val fullText: List<TreeNodeTextFragment>,
  override val toolTip: String?,
) : TreeNodePresentation

@ApiStatus.Internal
data class TreeNodeTextFragmentImpl(
  override val text: String,
  override val attributes: SimpleTextAttributes,
) : TreeNodeTextFragment
