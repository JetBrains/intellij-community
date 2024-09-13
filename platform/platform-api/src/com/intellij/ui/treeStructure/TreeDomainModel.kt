// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.openapi.components.service
import com.intellij.openapi.util.NlsContexts
import com.intellij.openapi.util.NlsSafe
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.LeafState
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.ApiStatus.Internal
import javax.swing.Icon

@ApiStatus.Experimental
fun TreeDomainModel(structure: AbstractTreeStructure, useReadAction: Boolean, concurrency: Int): TreeDomainModel =
  service<TreeDomainModelFactory>().createTreeDomainModel(structure, useReadAction, concurrency)

@ApiStatus.Experimental
interface TreeDomainModel {
  suspend fun <T> accessData(accessor: () -> T): T
  suspend fun computeRoot(): TreeNodeDomainModel?
}

@ApiStatus.Experimental
interface TreeNodeDomainModel {
  val userObject: Any
  suspend fun computeLeafState(): LeafState
  suspend fun computePresentation(builder: TreeNodePresentationBuilder): Flow<TreeNodePresentation>
  suspend fun computeChildren(): List<TreeNodeDomainModel>
}

@ApiStatus.Experimental
interface TreeNodePresentationBuilder {
  fun setIcon(icon: Icon?)
  fun setMainText(text: String)
  fun appendTextFragment(text: String, attributes: SimpleTextAttributes)
  fun setToolTipText(toolTip: String?)
  fun build(): TreeNodePresentation
}

@ApiStatus.Experimental
sealed interface TreeNodePresentation {
  val isLeaf: Boolean
  val icon: Icon?
  @get:NlsSafe val mainText: String
  val fullText: List<TreeNodeTextFragment>
  @get:NlsContexts.Tooltip val toolTip: String?
}

@ApiStatus.Experimental
sealed interface TreeNodeTextFragment {
  @get:NlsSafe val text: String
  val attributes: SimpleTextAttributes
}

@Internal
interface TreeDomainModelFactory {
  fun createTreeDomainModel(structure: AbstractTreeStructure, useReadAction: Boolean, concurrency: Int): TreeDomainModel
}
