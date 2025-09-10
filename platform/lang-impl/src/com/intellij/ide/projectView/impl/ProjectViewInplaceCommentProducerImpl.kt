// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.projectView.impl

import com.intellij.ide.projectView.ProjectViewNode
import com.intellij.ide.projectView.impl.nodes.getVirtualFileForNodeOrItsPSI
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.InplaceCommentAppender
import com.intellij.internal.visitChildrenInVfsRecursively
import com.intellij.openapi.components.service
import com.intellij.openapi.fileEditor.ex.IdeDocumentHistory
import com.intellij.openapi.fileEditor.impl.IdeDocumentHistoryImpl
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.isFile
import com.intellij.openapi.vfs.newvfs.impl.VirtualDirectoryImpl
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.tree.project.ProjectFileNode
import com.intellij.util.text.DateFormatUtil

internal fun appendInplaceComments(node: ProjectViewNode<*>, appender: InplaceCommentAppender) {
  if (node.shouldTryToShowInplaceComments()) {
    appendInplaceComments(appender, node.project, node.getVirtualFileForNodeOrItsPSI())
  }
}

private fun ProjectViewNode<*>.shouldTryToShowInplaceComments(): Boolean {
  // Most of this stuff is about avoiding inplace comments for members (UX-159).
  val content = value
  if (content.cantBePsiMember()) {
    return true
  }
  val parentNode = parent ?: return false // It's the root? We don't even show it, so don't bother with further checks.
  return cantBeFile(parentNode) // All members belong to files.
}

private fun Any?.cantBePsiMember(): Boolean {
  return this == null ||
         this is PsiFileSystemItem || // The node itself is either a file or directory, not a member.
         this !is PsiElement // All members inherit from PsiElement, so it's something else (e.g. a plain text file).
}

private fun <T> cantBeFile(node: AbstractTreeNode<T>): Boolean {
  return isDirectory(node) || // Either we know for sure it's a directory,
         !isFile(node) // or we know for sure it's not a file (could be neither, e.g. a package in Packages View).
}

private fun <T> isDirectory(node: AbstractTreeNode<T>): Boolean {
  val value = node.value
  // The ProjectFileNode check is for Scope-based views that don't use PsiDirectory.
  return value is PsiDirectory || (value is ProjectFileNode && value.virtualFile.isDirectory)
}

private fun <T> isFile(node: AbstractTreeNode<T>): Boolean = node.getVirtualFileForNodeOrItsPSI()?.isFile == true

// To be used in Rider once it migrates from legacy logic, don't change the signature and/or visibility.
fun appendInplaceComments(appender: InplaceCommentAppender, project: Project?, file: VirtualFile?) {
  val fileAttributes = getFileAttributes(file)
  if (fileAttributes != null) {
    appender.append("  ", SimpleTextAttributes.REGULAR_ATTRIBUTES)
    val attributes = SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES
    appender.append(DateFormatUtil.formatDateTime(fileAttributes.lastModifiedTime().toMillis()), attributes)
    appender.append(", " + StringUtil.formatFileSize(fileAttributes.size()), attributes)
  }

  if (Registry.`is`("show.last.visited.timestamps") && file != null && project != null) {
    (IdeDocumentHistory.getInstance(project) as IdeDocumentHistoryImpl).appendTimestamp(appender, file)
  }
}

internal fun appendVfsInfo(node: ProjectViewNode<*>, appender: InplaceCommentAppender) {
  val file = node.getVirtualFileForNodeOrItsPSI()
  val showCachedChildrenLimit = Registry.intValue("project.view.show.vfs.cached.children.count.limit")
  if (file !is VirtualDirectoryImpl || showCachedChildrenLimit <= 0) {
    return
  }
  node.project?.service<ProjectViewVfsInfoUpdater>() // start automatic update
  var count = 0
  visitChildrenInVfsRecursively(file) {
    count++
    count < showCachedChildrenLimit
  }
  count-- // don't count the directory itself
  val countString = if (count <= showCachedChildrenLimit) count.toString() else "$count+"
  appender.append(" $countString VFS ${if (count == 1) "file" else "files"}", SimpleTextAttributes.GRAYED_SMALL_ATTRIBUTES)
}
