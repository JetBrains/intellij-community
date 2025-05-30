// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.icons.AllIcons
import com.intellij.ide.IdeBundle
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.bookmark.ui.tree.computeDirectoryChildren
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class RootTypeNode(project: Project, bookmark: RootTypeBookmark) : BookmarkNode<RootTypeBookmark>(project, bookmark) {

  override fun canRepresent(element: Any?): Boolean = virtualFile?.equals(element) ?: false
  override fun contains(file: VirtualFile): Boolean = value?.type?.containsFile(file) ?: false

  override fun getVirtualFile(): VirtualFile? = value?.file
  override fun getChildren(): Collection<AbstractTreeNode<*>> = computeDirectoryChildren()

  override fun update(presentation: PresentationData) {
    presentation.setIcon(wrapIcon(AllIcons.Nodes.Folder))
    addTextTo(presentation, value?.type?.displayName ?: "", IdeBundle.message("scratches.and.consoles"))
  }
}
