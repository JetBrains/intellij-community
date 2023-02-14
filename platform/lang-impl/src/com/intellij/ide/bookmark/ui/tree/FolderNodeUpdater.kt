// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui.tree

import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.openapi.vfs.VfsUtilCore.isAncestor
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.tree.AsyncTreeModel
import com.intellij.ui.tree.TreeCollector
import com.intellij.ui.tree.project.ProjectFileNodeUpdater
import javax.swing.tree.TreePath

internal class FolderNodeUpdater(val view: BookmarksView) : ProjectFileNodeUpdater(view.project, view.model.invoker) {

  override fun updateStructure(fromRoot: Boolean, updatedFiles: Set<VirtualFile>) {
    val manager = BookmarksManager.getInstance(view.project) ?: return

    val bookmarkedFiles = mutableSetOf<VirtualFile>()
    manager.bookmarks.mapNotNullTo(bookmarkedFiles) { if (it is FileBookmark && it.file.isValid) it.file else null }
    if (bookmarkedFiles.isEmpty()) return // nothing to update

    val roots = mutableSetOf<VirtualFile>()
    for (bookmarkedFile in bookmarkedFiles) {
      if (fromRoot || updatedFiles.contains(bookmarkedFile)) {
        roots.add(bookmarkedFile)
      }
      else if (bookmarkedFile.isDirectory) {
        val collector = TreeCollector.VirtualFileRoots.create()
        for (updatedFile in updatedFiles) {
          val root = if (updatedFile.isDirectory) updatedFile else updatedFile.parent ?: continue
          if (isAncestor(bookmarkedFile, root, false)) collector.add(updatedFile)
        }
        roots.addAll(collector.get())
      }
    }
    roots.forEach { invalidate(it, true) }
  }

  fun invalidate(file: VirtualFile, structure: Boolean) = forEachTreePath(file) {
    view.model.invalidate(it, structure)
  }

  private fun forEachTreePath(file: VirtualFile, action: (TreePath) -> Unit) {
    val model = view.tree.model as? AsyncTreeModel ?: return
    val paths = mutableListOf<TreePath>()
    model.accept(VirtualFileVisitor(file, paths), false).onSuccess { paths.forEach(action) }
  }
}
