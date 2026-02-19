// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.icons.AllIcons.Nodes
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.ui.SimpleTextAttributes

internal class ModuleNode(project: Project, bookmark: ModuleBookmark) : BookmarkNode<ModuleBookmark>(project, bookmark) {

  override fun getChildren(): List<AbstractTreeNode<*>> = emptyList()

  override fun update(presentation: PresentationData) {
    presentation.setIcon(wrapIcon(if (value.isGroup) Nodes.ModuleGroup else Nodes.Module))
    presentation.tooltip = bookmarkDescription
    value.name
      .also { presentation.presentableText = it } // configure speed search
      .also { presentation.addText(it, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES) }
  }
}
