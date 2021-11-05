// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarksListener
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.openapi.actionSystem.CommonDataKeys
import com.intellij.openapi.actionSystem.DataContext
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.actionSystem.EditorAction
import com.intellij.openapi.editor.actionSystem.EditorActionHandler
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference

internal class NextBookmarkAction : EditorAction(NextBookmarkInEditorActionHandler(true)) {
  init {
    templatePresentation.setText(messagePointer("bookmark.go.to.next.action.text"))
  }
}

internal class PreviousBookmarkAction : EditorAction(NextBookmarkInEditorActionHandler(false)) {
  init {
    templatePresentation.setText(messagePointer("bookmark.go.to.previous.action.text"))
  }
}

internal class NextBookmarkInEditorActionHandler(val forward: Boolean) : EditorActionHandler() {
  override fun isEnabledForCaret(editor: Editor, caret: Caret, context: DataContext) = getNextBookmark(editor, context) != null
  override fun doExecute(editor: Editor, caret: Caret?, context: DataContext) {
    getNextBookmark(editor, context)?.let {
      if (it.canNavigate()) {
        it.navigate(true)
      }
    }
  }

  private fun getNextBookmark(editor: Editor, context: DataContext): LineBookmark? {
    val project = editor.project ?: CommonDataKeys.PROJECT.getData(context) ?: return null
    val provider = LineBookmarkProvider.find(project) ?: return null
    return project.service<NextBookmarkService>().next(forward, provider.createBookmark(editor) as? LineBookmark)
  }
}


internal class NextBookmarkService(private val project: Project) : BookmarksListener, Comparator<LineBookmark> {
  private val cache = AtomicReference<List<LineBookmark>>()
  private val bookmarks: List<LineBookmark>
    get() = BookmarksManager.getInstance(project)?.bookmarks?.filterIsInstance<LineBookmark>()?.sortedWith(this) ?: emptyList()

  private fun getCachedBookmarks() = synchronized(cache) {
    cache.get() ?: bookmarks.also { cache.set(it) }
  }

  override fun bookmarkAdded(group: BookmarkGroup, bookmark: Bookmark) = bookmarksChanged(bookmark)
  override fun bookmarkRemoved(group: BookmarkGroup, bookmark: Bookmark) = bookmarksChanged(bookmark)
  private fun bookmarksChanged(bookmark: Bookmark) {
    if (bookmark is LineBookmark) cache.set(null)
  }

  private fun compareFiles(bookmark1: LineBookmark, bookmark2: LineBookmark) = bookmark1.file.path.compareTo(bookmark2.file.path)
  private fun compareLines(bookmark1: LineBookmark, bookmark2: LineBookmark) = bookmark1.line.compareTo(bookmark2.line)
  override fun compare(bookmark1: LineBookmark, bookmark2: LineBookmark) = when (val result = compareFiles(bookmark1, bookmark2)) {
    0 -> compareLines(bookmark1, bookmark2)
    else -> result
  }

  private fun next(forward: Boolean, index: Int) = when {
    index < 0 -> (-index - if (forward) 1 else 2)
    else -> (index - if (forward) -1 else 1)
  }

  fun next(forward: Boolean, bookmark: LineBookmark?): LineBookmark? {
    val bookmarks = getCachedBookmarks().ifEmpty { return null }
    val index = bookmark?.let { next(forward, bookmarks.binarySearch(it, this)) } ?: if (forward) 0 else bookmarks.lastIndex
    return when {
      index < 0 -> bookmarks.last()
      index < bookmarks.size -> bookmarks[index]
      else -> bookmarks.first()
    }
  }

  init {
    project.messageBus.connect(project).subscribe(BookmarksListener.TOPIC, this)
  }
}
