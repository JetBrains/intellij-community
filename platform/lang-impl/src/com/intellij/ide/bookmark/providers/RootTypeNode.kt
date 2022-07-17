// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.icons.AllIcons
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.bookmark.ui.tree.computeDirectoryChildren
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.scratch.ScratchesNamedScope
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

internal class RootTypeNode(project: Project, bookmark: RootTypeBookmark) : BookmarkNode<RootTypeBookmark>(project, bookmark) {

  override fun canRepresent(element: Any?) = virtualFile?.equals(element) ?: false
  override fun contains(file: VirtualFile) = value?.type?.containsFile(file) ?: false

  override fun getVirtualFile() = value?.file
  override fun getChildren() = computeDirectoryChildren()

  override fun update(presentation: PresentationData) {
    presentation.setIcon(wrapIcon(AllIcons.Nodes.Folder))
    addTextTo(presentation, value?.type?.displayName ?: "", ScratchesNamedScope.scratchesAndConsoles())
  }
}
