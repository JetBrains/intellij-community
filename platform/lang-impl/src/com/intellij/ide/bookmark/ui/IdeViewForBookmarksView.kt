// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.IdeView
import com.intellij.ide.bookmark.ui.tree.FolderNode
import com.intellij.ide.util.DirectoryChooserUtil
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.openapi.vfs.validOrNull
import com.intellij.psi.PsiDirectory
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiUtilCore
import com.intellij.util.ui.tree.TreeUtil

internal class IdeViewForBookmarksView(private val view: BookmarksView,
                                       private val selection: List<AbstractTreeNode<*>>?) : IdeView {

  override fun getOrChooseDirectory(): PsiDirectory? = DirectoryChooserUtil.getOrChooseDirectory(this)

  override fun getDirectories(): Array<PsiDirectory> {
    if (selection.isNullOrEmpty()) return PsiDirectory.EMPTY_ARRAY
    if (selection.any { TreeUtil.getParentNodeOfType(it, FolderNode::class.java) == null }) return PsiDirectory.EMPTY_ARRAY
    val manager = PsiManager.getInstance(view.project)
    val directories = selection.mapNotNull { it.asVirtualFile()?.validOrNull()?.let { manager.findDirectory(it) } }
    return if (directories.isEmpty()) PsiDirectory.EMPTY_ARRAY else directories.toTypedArray()
  }

  override fun selectElement(element: PsiElement) {
    PsiUtilCore.getVirtualFile(element)?.let { view.select(it) }
  }
}
