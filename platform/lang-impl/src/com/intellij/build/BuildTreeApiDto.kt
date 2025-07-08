// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.ide.rpc.NavigatableId
import com.intellij.ide.ui.icons.IconId
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls


@Internal
@Serializable
sealed interface BuildTreeEvent
@Internal
@Serializable
data class BuildNodesUpdate(val nodes: List<BuildTreeNode>) : BuildTreeEvent // empty list requests clearing all nodes
@Internal
@Serializable
data class BuildTreeExposeRequest(val nodeId: Int?, val alsoSelect: Boolean) : BuildTreeEvent

@Internal
@Serializable
data class BuildTreeNode(
  val id: Int,   // 0 - root node, 1 - build progress root node, 2+ - the rest
  val parentId: Int,
  val icon: IconId?,
  val title: @Nls String?,
  val name: @Nls String?,
  val hint: @Nls String?,
  val duration: @Nls String?,
  val navigatables: List<NavigatableId>,
  val autoExpand: Boolean,
  val hasProblems: Boolean,
  val visibleAlways: Boolean,
  val visibleAsSuccessful: Boolean,
  val visibleAsWarning: Boolean
) {
  companion object {
    const val NO_ID: Int = -1
    const val ROOT_ID: Int = 0
    const val BUILD_PROGRESS_ROOT_ID: Int = 1
  }
}

@Internal
@Serializable
data class BuildTreeFilteringState(val showSuccessful: Boolean, val showWarnings: Boolean)

@Internal
@Serializable
data class BuildTreeNavigationContext(val hasPrevNode: Boolean, val hasNextNode: Boolean, val hasAnyNode: Boolean)

@Internal
@Serializable
data class BuildTreeNavigationRequest(val forward: Boolean)