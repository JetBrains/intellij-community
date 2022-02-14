// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.icons.AllIcons
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchesNamedScope
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VirtualFile

internal class RootTypeNode(project: Project, bookmark: RootTypeBookmark) : BookmarkNode<RootTypeBookmark>(project, bookmark) {

  override fun canRepresent(element: Any?) = virtualFile?.equals(element) ?: false
  override fun contains(file: VirtualFile) = value?.type?.containsFile(file) ?: false

  override fun getVirtualFile(): VirtualFile? = value
    ?.let { ScratchFileService.getInstance().getRootPath(it.type) }
    ?.let { LocalFileSystem.getInstance().findFileByPath(it) }

  override fun getChildren() = emptyList<AbstractTreeNode<*>>()

  override fun update(presentation: PresentationData) {
    presentation.setIcon(wrapIcon(AllIcons.Nodes.Folder))
    presentation.presentableText = value?.type?.displayName
    presentation.locationString = ScratchesNamedScope.scratchesAndConsoles()
    presentation.tooltip = bookmarkDescription
  }
}
