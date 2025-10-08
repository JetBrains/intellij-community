// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.build

import com.intellij.ide.rpc.NavigatableId
import com.intellij.ide.rpc.navigatable
import com.intellij.ide.ui.icons.IconId
import com.intellij.ide.ui.icons.icon
import kotlinx.serialization.Serializable
import org.jetbrains.annotations.ApiStatus.Internal
import org.jetbrains.annotations.Nls


@Internal
@Serializable
sealed interface BuildTreeEvent

@Internal
@Serializable
data class BuildNodesUpdate(
  val currentTimestamp: Long,
  val nodes: List<BuildTreeNode> // empty list requests clearing all nodes
) : BuildTreeEvent

@Internal
@Serializable
data class BuildTreeExposeRequest(val nodeId: Int?, val alsoSelect: Boolean) : BuildTreeEvent


@Internal
@Serializable
data class BuildTreeFilteringState(val showSuccessful: Boolean, val showWarnings: Boolean) : BuildTreeEvent

@Internal
@Serializable
data class BuildTreeNode(
  val id: Int,   // 0 - root node, 1 - build progress root node, 2+ - the rest
  val parentId: Int,
  val icon: IconId?,
  val title: @Nls String?,
  val name: @Nls String?,
  val hint: @Nls String?,
  val duration: BuildDuration?,
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

  // The method is overridden to have a special handling for icon and navigatable references,
  // in particular we want to avoid triggering the serialization of Navigatable objects, which can cause running a read action.
  override fun equals(other: Any?): Boolean {
    return other is BuildTreeNode &&
           id == other.id &&
           parentId == other.parentId &&
           title == other.title &&
           name == other.name &&
           hint == other.hint &&
           duration == other.duration &&
           autoExpand == other.autoExpand &&
           hasProblems == other.hasProblems &&
           visibleAlways == other.visibleAlways &&
           visibleAsSuccessful == other.visibleAsSuccessful &&
           visibleAsWarning == other.visibleAsWarning &&
           icon?.icon() === other.icon?.icon() &&
           navigatables.size == other.navigatables.size &&
           navigatables.indices.all { navigatables[it].navigatable() === other.navigatables[it].navigatable() }
  }

  override fun hashCode(): Int {
    return id
  }

  // used in tests
  override fun toString(): String {
    return name.toString()
  }
}

@Internal
@Serializable
sealed interface BuildDuration {
  @Internal
  @Serializable
  data class Fixed(val durationMs: Long) : BuildDuration

  @Internal
  @Serializable
  data class InProgress(val startTimestamp: Long) : BuildDuration
}

@Internal
@Serializable
data class BuildTreeNavigationContext(val hasPrevNode: Boolean, val hasNextNode: Boolean, val hasAnyNode: Boolean)

@Internal
@Serializable
data class BuildTreeNavigationRequest(val forward: Boolean)