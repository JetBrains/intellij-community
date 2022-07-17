// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import org.jetbrains.annotations.ApiStatus
import java.util.concurrent.atomic.AtomicBoolean

@ApiStatus.Internal
class FileNode(project: Project, bookmark: FileBookmark) : BookmarkNode<FileBookmark>(project, bookmark) {
  private val nodes = mutableListOf<AbstractTreeNode<*>>()
  private val grouped = AtomicBoolean()

  fun grouped() = when (grouped.getAndSet(true)) {
    true -> null
    else -> this
  }

  fun grouped(node: AbstractTreeNode<*>): FileNode? {
    nodes += node.also { it.parent = this }
    return grouped()
  }

  fun ungroup() {
    grouped.set(false)
    nodes.clear()
  }

  override fun getChildren() = nodes.toList()

  override fun getVirtualFile() = value.file
}
