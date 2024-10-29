// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.LineBookmark
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.SimpleTextAttributes
import org.jetbrains.annotations.ApiStatus

@ApiStatus.Internal
class LineNode(project: Project, bookmark: LineBookmark) : BookmarkNode<LineBookmark>(project, bookmark) {

  override fun getVirtualFile(): VirtualFile = value.file

  override fun isAlwaysLeaf(): Boolean = true
  override fun getChildren(): List<AbstractTreeNode<*>> = emptyList()

  override fun update(presentation: PresentationData) {
    val line = value.line + 1
    presentation.setIcon(wrapIcon(null))
    if (parent is FileNode) {
      presentation.addText("$line: ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      bookmarkDescription
        ?.also { presentation.presentableText = it } // configure speed search
        ?.also { presentation.addText(it, SimpleTextAttributes.REGULAR_ATTRIBUTES) }
    }
    else {
      addTextTo(presentation, value.file, line)
    }
  }
}
