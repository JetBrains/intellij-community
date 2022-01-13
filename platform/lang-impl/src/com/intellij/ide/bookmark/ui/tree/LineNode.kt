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
    val description = bookmarkDescription

    presentation.setIcon(wrapIcon(null))
    presentation.tooltip = description
    if (parent !is FileNode) {
      presentation.addText(value.file.name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      presentation.addText(" :$line", SimpleTextAttributes.GRAYED_ATTRIBUTES)
    }
    else if (!description.isNullOrBlank()) {
      presentation.addText("$line: ", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      presentation.addText(description, SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
    else {
      presentation.addText(line.toString(), SimpleTextAttributes.REGULAR_ATTRIBUTES)
    }
  }
}
