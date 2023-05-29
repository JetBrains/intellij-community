// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.projectView

import org.jetbrains.annotations.ApiStatus

enum class NodeSortKey {
  BY_NAME,
  BY_TYPE,
  BY_TIME_ASCENDING,
  BY_TIME_DESCENDING,
}

@ApiStatus.Experimental
sealed class NodeSortSettings {

  abstract val sortKey: NodeSortKey
  abstract val isManualOrder: Boolean
  abstract val isFoldersAlwaysOnTop: Boolean
  abstract val isSortByType: Boolean

  companion object {
    @JvmStatic
    @ApiStatus.Internal
    fun of(isManualOrder: Boolean, sortKey: NodeSortKey, isFoldersAlwaysOnTop: Boolean): NodeSortSettings =
      Impl(isManualOrder, sortKey, isFoldersAlwaysOnTop)
  }

  private data class Impl(
    override val isManualOrder: Boolean,
    override val sortKey: NodeSortKey,
    override val isFoldersAlwaysOnTop: Boolean,
  ) : NodeSortSettings() {
    override val isSortByType: Boolean
      get() = sortKey == NodeSortKey.BY_TYPE
  }

}
