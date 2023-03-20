// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.ide.projectView.impl.CompoundTreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.AbstractTreeStructure
import com.intellij.ide.util.treeView.NodeDescriptor

class BookmarksTreeStructure(val panel: BookmarksView) : AbstractTreeStructure() {
  private val root = RootNode(panel)

  override fun commit() = Unit
  override fun hasSomethingToCommit() = false

  override fun createDescriptor(element: Any, parent: NodeDescriptor<*>?) = element as NodeDescriptor<*>

  override fun getRootElement(): Any = root
  override fun getParentElement(element: Any): Any? = element.asAbstractTreeNode?.parent
  override fun getChildElements(element: Any): Array<Any> {
    val node = element as? AbstractTreeNode<*>
    if (panel.isPopup && node !is RootNode && node !is GroupNode) return emptyArray()
    val children = node?.children?.ifEmpty { null } ?: return emptyArray()
    val parent = node.parentFolderNode ?: return children.toTypedArray()
    val provider = CompoundTreeStructureProvider.get(panel.project) ?: return children.toTypedArray()
    return provider.modify(node, children, parent.settings).toTypedArray()
  }
}
