// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.*
import com.intellij.ide.favoritesTreeView.AbstractUrlFavoriteAdapter
import com.intellij.ide.projectView.impl.DirectoryUrl
import com.intellij.ide.projectView.impl.PsiFileUrl
import com.intellij.ide.util.treeView.AbstractTreeNode
import com.intellij.ide.util.treeView.NodeDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.BulkAwareDocumentListener.Simple
import com.intellij.openapi.editor.event.EditorMouseEvent
import com.intellij.openapi.editor.event.EditorMouseEventArea
import com.intellij.openapi.editor.event.EditorMouseListener
import com.intellij.openapi.fileEditor.FileDocumentManager
import com.intellij.openapi.progress.ProcessCanceledException
import com.intellij.openapi.project.DumbService
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.psi.PsiCompiledElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFileSystemItem
import com.intellij.testFramework.LightVirtualFile
import java.awt.event.MouseEvent
import javax.swing.SwingUtilities

class LineBookmarkProvider(private val project: Project) : BookmarkProvider, EditorMouseListener, Simple {
  override fun getWeight() = Int.MIN_VALUE
  override fun getProject() = project

  override fun compare(bookmark1: Bookmark, bookmark2: Bookmark): Int {
    val fileBookmark1 = bookmark1 as? FileBookmark
    val fileBookmark2 = bookmark2 as? FileBookmark
    if (fileBookmark1 == null && fileBookmark2 == null) return 0
    if (fileBookmark1 == null) return -1
    if (fileBookmark2 == null) return 1
    val file1 = fileBookmark1.file
    val file2 = fileBookmark2.file
    if (file1 == file2) {
      val lineBookmark1 = bookmark1 as? LineBookmark
      val lineBookmark2 = bookmark2 as? LineBookmark
      if (lineBookmark1 == null && lineBookmark2 == null) return 0
      if (lineBookmark1 == null) return -1
      if (lineBookmark2 == null) return 1
      return lineBookmark1.line.compareTo(lineBookmark2.line)
    }
    if (file1.isDirectory && !file2.isDirectory) return -1
    if (!file1.isDirectory && file2.isDirectory) return 1
    return StringUtil.naturalCompare(file1.presentableName, file2.presentableName)
  }

  override fun createBookmark(map: Map<String, String>) = createBookmark(map["url"], StringUtil.parseInt(map["line"], -1))

  override fun createBookmark(context: Any?): FileBookmark? = when (context) {
    is AbstractTreeNode<*> -> createBookmark(context.value)
    is NodeDescriptor<*> -> createBookmark(context.element)
    // below // migrate old bookmarks and favorites
    is com.intellij.ide.bookmarks.Bookmark -> createBookmark(context.file, context.line) //
    is AbstractUrlFavoriteAdapter -> createBookmark(context)
    is DirectoryUrl -> createBookmark(context.url)
    is PsiFileUrl -> createBookmark(context.url)
    // above // migrate old bookmarks and favorites
    is PsiElement -> createBookmark(context)
    is VirtualFile -> createBookmark(context, -1)
    else -> null
  }

  fun createBookmark(file: VirtualFile, line: Int = -1): FileBookmark? = when {
    !file.isValid || file is LightVirtualFile -> null
    line >= 0 -> LineBookmarkImpl(this, file, line)
    else -> FileBookmarkImpl(this, file)
  }

  fun createBookmark(editor: Editor, line: Int? = null): Bookmark? {
    if (editor.isOneLineMode) return null
    val file = FileDocumentManager.getInstance().getFile(editor.document) ?: return null
    return createBookmark(file, line ?: editor.caretModel.logicalPosition.line)
  }

  private fun createBookmark(url: String?, line: Int = -1) = url
    ?.let { VirtualFileManager.getInstance().findFileByUrl(it) }
    ?.let { createBookmark(it, line) }

  private fun createBookmark(adapter: AbstractUrlFavoriteAdapter) = when {
    !adapter.nodeProvider::class.java.name.startsWith("com.intellij.ide.favoritesTreeView.Psi") -> null
    DumbService.isDumb(project) -> throw ProcessCanceledException() // wait for the end of indexing
    else -> adapter.createPath(project)?.singleOrNull()?.let { createBookmark(it) }
  }

  private fun createBookmark(element: PsiElement): FileBookmark? {
    if (element is PsiFileSystemItem) return element.virtualFile?.let { createBookmark(it) }
    if (element is PsiCompiledElement) return null
    val file = element.containingFile.virtualFile ?: return null
    if (file is LightVirtualFile) return null
    val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
    return createBookmark(file, document.getLineNumber(element.textOffset))
  }

  private val MouseEvent.isUnexpected
    get() = !SwingUtilities.isLeftMouseButton(this) || isPopupTrigger || if (SystemInfo.isMac) !isMetaDown else !isControlDown

  private val EditorMouseEvent.isUnexpected
    get() = isConsumed || area != EditorMouseEventArea.LINE_MARKERS_AREA || mouseEvent.isUnexpected

  override fun mouseClicked(event: EditorMouseEvent) {
    if (event.isUnexpected) return
    val manager = BookmarksManager.getInstance(project) ?: return
    val bookmark = createBookmark(event.editor, event.logicalPosition.line) ?: return
    manager.getType(bookmark)?.let { manager.remove(bookmark) } ?: manager.add(bookmark, BookmarkType.DEFAULT)
    event.consume()
  }

  override fun afterDocumentChange(document: Document) {
    val file = FileDocumentManager.getInstance().getFile(document) ?: return
    if (file is LightVirtualFile) return
    val manager = BookmarksManager.getInstance(project) ?: return
    val map = sortedMapOf<LineBookmarkImpl, Int>(compareBy { it.line })
    val set = mutableSetOf<Int>()
    for (bookmark in manager.bookmarks) {
      if (bookmark is LineBookmarkImpl && bookmark.file == file) {
        val line = bookmark.descriptor.rangeMarker?.let { if (it.isValid) it.document.getLineNumber(it.startOffset) else null } ?: -1
        when (bookmark.line) {
          line -> set.add(line)
          else -> map[bookmark] = line
        }
      }
    }
    if (map.isEmpty()) return
    val bookmarks = mutableMapOf<Bookmark, Bookmark?>()
    map.forEach { (bookmark, line) ->
      bookmarks[bookmark] = when {
        line < 0 || set.contains(line) -> null
        else -> {
          set.add(line)
          createBookmark(file, line)
        }
      }
    }
    manager.update(bookmarks)
  }

  init {
    if (!project.isDefault) {
      val multicaster = EditorFactory.getInstance().eventMulticaster
      multicaster.addDocumentListener(this, project)
      multicaster.addEditorMouseListener(this, project)
    }
  }

  companion object {
    @JvmStatic
    fun find(project: Project): LineBookmarkProvider? = when {
      project.isDisposed -> null
      else -> BookmarkProvider.EP.findExtension(LineBookmarkProvider::class.java, project)
    }

    fun readLineText(bookmark: LineBookmark?) = bookmark?.let { readLineText(it.file, it.line) }

    fun readLineText(file: VirtualFile, line: Int): String? {
      val document = FileDocumentManager.getInstance().getDocument(file) ?: return null
      val start = document.getLineStartOffset(line)
      if (start < 0) return null
      val end = document.getLineEndOffset(line)
      if (end < start) return null
      return document.getText(TextRange.create(start, end))
    }
  }
}
