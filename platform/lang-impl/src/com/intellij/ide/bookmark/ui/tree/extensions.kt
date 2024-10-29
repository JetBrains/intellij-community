// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.icons.AllIcons
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ViewSettings
import com.intellij.ide.projectView.impl.CompoundIconProvider.findIcon
import com.intellij.ide.projectView.impl.nodes.ProjectViewDirectoryHelper
import com.intellij.ide.projectView.impl.nodes.PsiFileNode
import com.intellij.ide.scratch.RootType
import com.intellij.ide.scratch.ScratchFileService
import com.intellij.ide.scratch.ScratchTreeStructureProvider
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.Iconable
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiUtilCore.findFileSystemItem
 import com.intellij.util.IconUtil
import javax.swing.Icon

internal val Any.asAbstractTreeNode: AbstractTreeNode<*>?
  get() = this as? AbstractTreeNode<*>

internal val AbstractTreeNode<*>.bookmarksManager: BookmarksManager?
  get() = BookmarksManager.getInstance(project)

internal val AbstractTreeNode<*>.parentRootNode: RootNode?
  get() = this as? RootNode ?: parent?.parentRootNode

internal val AbstractTreeNode<*>.parentFolderNode: FolderNode?
  get() = this as? FolderNode ?: parent?.parentFolderNode

internal fun ProjectViewNode<*>.findFileIcon(): Icon? = findIcon(findFileSystemItem(project, virtualFile), 0)

internal fun ProjectViewNode<*>.computeExternalLocation(file: VirtualFile): @NlsSafe String? {
  return computeScratchPresentation(file)?.first ?: FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
}

internal fun ProjectViewNode<*>.computeScratchPresentation(file: VirtualFile): Pair<@NlsSafe String, Icon?>? {
  val type = ScratchFileService.findRootType(file) ?: return null
  val root = ScratchTreeStructureProvider.getVirtualFile(type) ?: return null
  return when (root) {
    file -> type.displayName?.let { it to AllIcons.Nodes.Folder }
    file.parent -> type.substituteName(project, file)?.let {
      it to IconUtil.getIcon(file, Iconable.ICON_FLAG_VISIBILITY and Iconable.ICON_FLAG_READ_STATUS, project)
    }
    else -> null
  }
}

internal fun ProjectViewNode<*>.computeDirectoryChildren(): Collection<AbstractTreeNode<*>> {
  val directory = findFileSystemItem(project, virtualFile) as? PsiDirectory ?: return emptyList()
  if (ProjectFileIndex.getInstance(directory.project).getModuleForFile(directory.virtualFile, false) != null) {
    return ProjectViewDirectoryHelper.getInstance(directory.project).getDirectoryChildren(directory, settings, true)
  }
  val children = directory.virtualFile.children ?: return emptyList()
  val type = RootType.forFile(directory.virtualFile)
  return mutableListOf<AbstractTreeNode<*>>().apply {
    children.forEach {
      if (type == null || !type.isIgnored(directory.project, directory.virtualFile)) {
        when (val item = findFileSystemItem(directory.project, it)) {
          is PsiDirectory -> add(ExternalFolderNode(item, settings))
          is PsiFile -> add(PsiFileNode(item.project, item, settings))
        }
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
    val scratch = computeScratchPresentation(file)
    presentation.setIcon(scratch?.second ?: findFileIcon())
    presentation.presentableText = scratch?.first ?: file.presentableName
  }
}
