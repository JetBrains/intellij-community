// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.CompoundIconProvider.findIcon
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore.findFileSystemItem

internal val Any.asAbstractTreeNode
  get() = this as? AbstractTreeNode<*>

internal val AbstractTreeNode<*>.bookmarksManager
  get() = BookmarksManager.getInstance(project)

internal val AbstractTreeNode<*>.parentRootNode: RootNode?
  get() = this as? RootNode ?: parent?.parentRootNode

internal val AbstractTreeNode<*>.parentFolderNode: FolderNode?
  get() = this as? FolderNode ?: parent?.parentFolderNode

fun ProjectViewNode<*>.computeDirectoryChildren(): Collection<AbstractTreeNode<*>> {
  val directory = findFileSystemItem(project, virtualFile) as? PsiDirectory ?: return emptyList()
  if (ProjectFileIndex.getInstance(directory.project).getModuleForFile(directory.virtualFile, false) != null) {
    return ProjectViewDirectoryHelper.getInstance(directory.project).getDirectoryChildren(directory, settings, true)
  }
  val children = directory.virtualFile.children ?: return emptyList()
  return mutableListOf<AbstractTreeNode<*>>().apply {
    children.forEach {
      when (val item = findFileSystemItem(directory.project, it)) {
        is PsiDirectory -> add(ExternalFolderNode(item, settings))
        is PsiFile -> add(PsiFileNode(item.project, item, settings))
      }
    }
  }
}

private class ExternalFolderNode(directory: PsiDirectory, settings: ViewSettings?)
  : ProjectViewNode<PsiDirectory>(directory.project, directory, settings) {

  override fun canRepresent(element: Any?) = virtualFile?.equals(element) ?: false
  override fun contains(file: VirtualFile) = virtualFile?.let { VfsUtil.isAncestor(it, file, true) } ?: false

  override fun getVirtualFile() = value?.virtualFile
  override fun getChildren() = computeDirectoryChildren()

  override fun update(presentation: PresentationData) {
    val file = virtualFile ?: return
    presentation.setIcon(findIcon(findFileSystemItem(project, file), 0))
    presentation.presentableText = file.presentableName
  }
}
