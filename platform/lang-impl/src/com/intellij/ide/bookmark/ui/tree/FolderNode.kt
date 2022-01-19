// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiDirectory
import com.intellij.psi.util.PsiUtilCore

class FolderNode(project: Project, bookmark: FileBookmark) : BookmarkNode<FileBookmark>(project, bookmark) {

  override fun getVirtualFile() = value.file

  override fun getChildren(): Collection<AbstractTreeNode<*>> {
    val directory = PsiUtilCore.findFileSystemItem(project, value?.file) as? PsiDirectory ?: return emptyList()
    return ProjectViewDirectoryHelper.getInstance(project).getDirectoryChildren(directory, settings, true)
  }
}
