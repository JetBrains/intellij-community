// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import javax.swing.tree.TreePath

internal class GroupBookmarkVisitor(val group: BookmarkGroup, val bookmark: Bookmark? = null) : TreeVisitor {
  override fun visit(path: TreePath) = when (path.pathCount) {
    4 -> when (TreeUtil.getAbstractTreeNode(path)?.value) {
      bookmark -> TreeVisitor.Action.INTERRUPT
      else -> TreeVisitor.Action.SKIP_CHILDREN
    }
    3 -> when (TreeUtil.getAbstractTreeNode(path)?.value) {
      bookmark -> TreeVisitor.Action.INTERRUPT
      null -> TreeVisitor.Action.SKIP_CHILDREN
      else -> TreeVisitor.Action.CONTINUE
    }
    2 -> when (TreeUtil.getAbstractTreeNode(path)?.value) {
      group -> bookmark?.let { TreeVisitor.Action.CONTINUE } ?: TreeVisitor.Action.INTERRUPT
      else -> TreeVisitor.Action.SKIP_CHILDREN
    }
    1 -> TreeVisitor.Action.CONTINUE
    else -> TreeVisitor.Action.SKIP_CHILDREN
  }
}
