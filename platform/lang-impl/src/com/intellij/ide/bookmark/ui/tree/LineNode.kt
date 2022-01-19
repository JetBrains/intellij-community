// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.LineBookmark
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes

class LineNode(project: Project, bookmark: LineBookmark) : BookmarkNode<LineBookmark>(project, bookmark) {

  override fun getVirtualFile() = value.file

  override fun isAlwaysLeaf() = true
  override fun getChildren() = emptyList<AbstractTreeNode<*>>()

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
