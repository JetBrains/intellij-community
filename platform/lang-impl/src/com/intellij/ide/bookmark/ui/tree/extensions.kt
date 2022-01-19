// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.util.treeView.AbstractTreeNode

internal val Any.asAbstractTreeNode
  get() = this as? AbstractTreeNode<*>

internal val AbstractTreeNode<*>.bookmarksManager
  get() = BookmarksManager.getInstance(project)

internal val AbstractTreeNode<*>.parentRootNode: RootNode?
  get() = this as? RootNode ?: parent?.parentRootNode

internal val AbstractTreeNode<*>.parentFolderNode: FolderNode?
  get() = this as? FolderNode ?: parent?.parentFolderNode
