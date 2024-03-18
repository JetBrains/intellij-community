// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.actions

import com.intellij.ide.bookmark.*
import com.intellij.openapi.actionSystem.ActionUpdateThread
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import java.util.concurrent.atomic.AtomicReference

internal class NextBookmarkAction : IterateBookmarksAction(true)

internal class PreviousBookmarkAction : IterateBookmarksAction(false)

internal abstract class IterateBookmarksAction(val forward: Boolean) : DumbAwareAction() {
  private val AnActionEvent.nextBookmark
    get() = project?.service<NextBookmarkService>()?.next(forward, contextBookmark as? LineBookmark)

  override fun getActionUpdateThread(): ActionUpdateThread = ActionUpdateThread.BGT

  override fun update(event: AnActionEvent) {
    event.presentation.isEnabled = when (val view = event.bookmarksView) {
      null -> event.nextBookmark != null
      else -> if (forward) view.hasNextOccurence() else view.hasPreviousOccurence()
    }
  }

  override fun actionPerformed(event: AnActionEvent) {
    when (val view = event.bookmarksView) {
      null -> event.nextBookmark?.run { if (canNavigate()) navigate(true) }
      else -> if (forward) view.goNextOccurence() else view.goPreviousOccurence()
    }
  }
}

@Service(Service.Level.PROJECT)
internal class NextBookmarkService(private val project: Project) : BookmarksListener, Comparator<LineBookmark> {
  private val cache = AtomicReference<List<LineBookmark>>()
  private val bookmarks: List<LineBookmark>
    get() = BookmarksManager.getInstance(project)?.bookmarks?.filterIsInstance<LineBookmark>()?.sortedWith(this) ?: emptyList()

  private fun getCachedBookmarks() = synchronized(cache) {
    cache.get() ?: bookmarks.also { cache.set(it) }
  }

  override fun bookmarkAdded(group: BookmarkGroup, bookmark: Bookmark): Unit = bookmarksChanged(bookmark)
  override fun bookmarkRemoved(group: BookmarkGroup, bookmark: Bookmark): Unit = bookmarksChanged(bookmark)
  private fun bookmarksChanged(bookmark: Bookmark) {
    if (bookmark is LineBookmark) cache.set(null)
  }

  private fun compareFiles(bookmark1: LineBookmark, bookmark2: LineBookmark) = bookmark1.file.path.compareTo(bookmark2.file.path)
  private fun compareLines(bookmark1: LineBookmark, bookmark2: LineBookmark) = bookmark1.line.compareTo(bookmark2.line)
  override fun compare(bookmark1: LineBookmark, bookmark2: LineBookmark): Int = when (val result = compareFiles(bookmark1, bookmark2)) {
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
