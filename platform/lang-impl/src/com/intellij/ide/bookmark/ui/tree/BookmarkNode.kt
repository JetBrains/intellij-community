// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.projectView.PresentationData
import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.ProjectViewSettings.Immutable.DEFAULT
import com.intellij.ide.projectView.impl.CompoundIconProvider.findIcon
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.openapi.util.NlsSafe
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.presentation.FilePresentationService
import com.intellij.psi.util.PsiUtilCore
import com.intellij.ui.BackgroundSupplier
import com.intellij.ui.IconManager
import com.intellij.ui.SimpleTextAttributes
import javax.swing.Icon

abstract class BookmarkNode<B : Bookmark>(project: Project, bookmark: B)
  : BackgroundSupplier, ProjectViewNode<B>(project, bookmark, DEFAULT) {

  val bookmarksView
    get() = parentRootNode?.value

  val bookmarkType
    get() = bookmarksManager?.getType(value)

  val bookmarkDescription
    get() = bookmarkGroup?.getDescription(value)?.ifBlank { null }

  var bookmarkGroup: BookmarkGroup? = null

  override fun canRepresent(element: Any?) = virtualFile?.equals(element) ?: false
  override fun contains(file: VirtualFile) = virtualFile?.let { VfsUtil.isAncestor(it, file, true) } ?: false

  override fun canNavigate() = value.canNavigate()
  override fun canNavigateToSource() = value.canNavigateToSource()
  override fun navigate(requestFocus: Boolean) = value.navigate(requestFocus)

  override fun computeBackgroundColor() = FilePresentationService.getFileBackgroundColor(project, virtualFile)
  override fun getElementBackground(row: Int) = presentation.background

  protected fun wrapIcon(icon: Icon?): Icon {
    val type = bookmarkType ?: BookmarkType.DEFAULT
    return when {
      icon == null -> type.icon
      type == BookmarkType.DEFAULT -> icon
      else -> IconManager.getInstance().createRowIcon(type.icon, icon)
    }
  }

  override fun update(presentation: PresentationData) {
    val file = virtualFile ?: return
    presentation.setIcon(wrapIcon(findIcon(PsiUtilCore.findFileSystemItem(project, file), 0)))
    addTextTo(presentation, file)
  }

  protected fun addTextTo(presentation: PresentationData, file: VirtualFile, line: Int = 0) {
    val name = file.presentableName
    val location = file.parent?.let { getRelativePath(it) }
    val description = bookmarkDescription
    if (description == null) {
      presentation.presentableText = name // configure speed search
      presentation.addText(name, SimpleTextAttributes.REGULAR_ATTRIBUTES)
      if (line > 0) presentation.addText(" :$line", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      location?.let { presentation.addText("  $it", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
    }
    else {
      presentation.presentableText = "$description $name" // configure speed search
      presentation.addText("$description  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
      presentation.addText(name, SimpleTextAttributes.GRAYED_ATTRIBUTES)
      if (line > 0) presentation.addText(" :$line", SimpleTextAttributes.GRAYED_ATTRIBUTES)
      location?.let { presentation.addText("  ($it)", SimpleTextAttributes.GRAYED_ATTRIBUTES) }
    }
  }

  private fun getRelativePath(file: VirtualFile): @NlsSafe String? {
    val project = project ?: return null
    if (project.isDisposed) return null
    val index = ProjectFileIndex.getInstance(project)
    index.getModuleForFile(file, false) ?: return FileUtil.getLocationRelativeToUserHome(file.presentableUrl)
    var root = file
    while (true) {
      val parent = root.parent ?: break
      index.getModuleForFile(parent, false) ?: break
      root = parent
    }
    return if (file == root) null else VfsUtil.getRelativePath(file, root)
  }
}
