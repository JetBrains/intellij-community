// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.BookmarkBundle.messagePointer
import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkGroup
import com.intellij.ide.bookmark.BookmarkOccurrence
import com.intellij.ide.bookmark.BookmarksListener
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.LineBookmark
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier

internal class NextBookmarkAction : IterateBookmarksAction(true, messagePointer("bookmark.go.to.next.action.text"))
internal class PreviousBookmarkAction : IterateBookmarksAction(false, messagePointer("bookmark.go.to.previous.action.text"))
internal abstract class IterateBookmarksAction(val forward: Boolean, dynamicText: Supplier<String>) : DumbAwareAction(dynamicText) {
  private val AnActionEvent.nextBookmark
    get() = project?.service<NextBookmarkService>()?.next(forward, contextBookmark as? LineBookmark)

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = when (val view = event.bookmarksViewFromToolWindow) {
      null -> event.nextBookmark != null
      else -> if (forward) view.hasNextOccurence() else view.hasPreviousOccurence()
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    when (val view = event.bookmarksViewFromToolWindow) {
      null -> event.nextBookmark?.run { if (canNavigate()) navigate(true) }
      else -> if (forward) view.goNextOccurence() else view.goPreviousOccurence()
    }
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
    if (bookmark != null) {
      val next = bookmarks.getOrNull(next(forward, bookmarks.binarySearch(bookmark, this)))
      if (next != null || !BookmarkOccurrence.cyclic) return next
    }
    return if (forward) bookmarks.first() else bookmarks.last()
  }

  init {
    project.messageBus.connect(project).subscribe(BookmarksListener.TOPIC, this)
  }
}
