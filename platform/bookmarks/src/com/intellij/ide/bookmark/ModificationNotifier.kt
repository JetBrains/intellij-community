// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark

import com.intellij.ide.bookmark.ui.BookmarksView
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import com.intellij.openapi.wm.ToolWindowId.BOOKMARKS
import com.intellij.openapi.wm.ToolWindowManager
import java.util.concurrent.atomic.AtomicLong

internal class ModificationNotifier(private val project: Project) : BookmarksListener {
  private val modification = AtomicLong()
  val count: Long
    get() = modification.get()

  private val publisher
    get() = when {
      !project.isOpen || project.isDisposed -> null
      else -> project.messageBus.syncPublisher(BookmarksListener.TOPIC)
    }

  internal fun selectLater(select: (BookmarksView) -> Unit): Unit = invokeLater {
    val window = if (project.isDisposed) null else ToolWindowManager.getInstance(project).getToolWindow(BOOKMARKS)
    val window1 = window
    val view = window1?.contentManagerIfCreated?.selectedContent?.component as? BookmarksView
    view?.let { if (it.isShowing) select(it) }
  }

  internal var snapshot: List<BookmarkOccurrence>? = null

  private fun notifyLater(notify: (BookmarksListener) -> Unit) {
    snapshot = null // cleanup cached snapshot
    modification.incrementAndGet()
    invokeLater { publisher?.let(notify) }
  }

  override fun groupsSorted(): Unit = notifyLater { it.groupsSorted() }
  override fun groupAdded(group: BookmarkGroup): Unit = notifyLater { it.groupAdded(group) }
  override fun groupRemoved(group: BookmarkGroup): Unit = notifyLater { it.groupRemoved(group) }
  override fun groupRenamed(group: BookmarkGroup): Unit = notifyLater { it.groupRenamed(group) }
  override fun bookmarksSorted(group: BookmarkGroup): Unit = notifyLater { it.bookmarksSorted(group) }
  override fun bookmarkAdded(group: BookmarkGroup, bookmark: Bookmark): Unit = notifyLater { it.bookmarkAdded(group, bookmark) }
  override fun bookmarkRemoved(group: BookmarkGroup, bookmark: Bookmark): Unit = notifyLater { it.bookmarkRemoved(group, bookmark) }
  override fun bookmarkChanged(group: BookmarkGroup, bookmark: Bookmark): Unit = notifyLater { it.bookmarkChanged(group, bookmark) }
  override fun bookmarkTypeChanged(bookmark: Bookmark): Unit = notifyLater { it.bookmarkTypeChanged(bookmark) }
  override fun defaultGroupChanged(old: BookmarkGroup?, new: BookmarkGroup?): Unit = notifyLater { it.defaultGroupChanged(old, new) }
}
