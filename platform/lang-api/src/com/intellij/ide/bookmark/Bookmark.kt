// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.pom.Navigatable

interface Bookmark : Navigatable {
  /**
   * @return a provider used to create this root
   */
  val provider: BookmarkProvider

  /**
   * @return attributes to save a bookmark and restore it with the provider
   * @see BookmarkProvider.createBookmark
   */
  val attributes: Map<String, String>

  /**
   * @return creates a tree node for the Bookmarks tool window
   * @see com.intellij.openapi.wm.ToolWindowId.BOOKMARKS
   */
  fun createNode(): AbstractTreeNode<*>

  /**
   * @return a hash code value for this bookmark
   */
  override fun hashCode(): Int

  /**
   * @param other the reference object with which to compare
   * @return `true` if this bookmark is the same as the `other` argument, `false` otherwise
   */
  override fun equals(other: Any?): Boolean
}
