// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.util.containers.toArray

class BookmarksTreeStructure(val panel: BookmarksView) : AbstractTreeStructure() {
  private val root = RootNode(panel)

  override fun commit() = Unit
  override fun hasSomethingToCommit() = false

  override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?) = element as NodeDescriptor<*>

  override fun getRootElement(): Any = root
  override fun getParentElement(element: Any): Any? = element.asAbstractTreeNode?.parent
  override fun getChildElements(element: Any): Array<Any> {
    val node = element as? AbstractTreeNode<*>
    val children = node?.children?.ifEmpty { null } ?: return emptyArray()
    if (node !is RootNode && node !is GroupNode && node !is FileNode && node !is LineNode) {
      //TODO:sort project view nodes
    }
    return children.toArray(arrayOf())
  }
}
