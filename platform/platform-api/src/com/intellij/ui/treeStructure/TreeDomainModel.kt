// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ui.treeStructure

import com.intellij.ui.SimpleTextAttributes
import kotlinx.coroutines.flow.Flow
import org.jetbrains.annotations.ApiStatus
import javax.swing.Icon

@ApiStatus.Experimental
interface TreeDomainModel {
  suspend fun computeRoot(): TreeNodeDomainModel?
}

@ApiStatus.Experimental
interface TreeNodeDomainModel {
  suspend fun computeIsLeaf(): Boolean
  suspend fun computePresentation(builder: TreeNodePresentationBuilder): Flow<TreeNodePresentation>
  suspend fun computeChildren(): List<TreeNodeDomainModel>
  @ApiStatus.Internal
  fun getUserObject(): Any
}

@ApiStatus.Experimental
sealed interface TreeNodePresentation

@ApiStatus.Experimental
interface TreeNodePresentationBuilder {
  fun setIcon(icon: Icon?)
  fun setMainText(text: String)
  fun appendTextFragment(text: String, attributes: SimpleTextAttributes)
  fun setToolTipText(toolTip: String?)
  fun build(): TreeNodePresentation
}
