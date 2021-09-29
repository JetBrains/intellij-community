// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project

class FileNode(project: Project, bookmark: FileBookmark) : BookmarkNode<FileBookmark>(project, bookmark) {
  private val nodes = mutableListOf<AbstractTreeNode<*>>()

  fun addChild(node: AbstractTreeNode<*>) {
    nodes += node.also { it.parent = this }
  }

  fun removeChildren() = nodes.clear()

  override fun getChildren() = nodes.toList()

  override fun getVirtualFile() = value.file
}
