// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.openapi.project.Project
import java.util.Objects

class BookmarkOccurrence internal constructor(
  val group: BookmarkGroup,
  val bookmark: Bookmark,
  private val current: Int,
  private val snapshot: List<BookmarkOccurrence>?
) {
  constructor(group: BookmarkGroup, bookmark: Bookmark) : this(group, bookmark, -1, null)

  fun nextFileBookmark() = next { it.bookmark is FileBookmark }
  fun nextLineBookmark() = next { it.bookmark is LineBookmark }
  fun next(predicate: (BookmarkOccurrence) -> Boolean): BookmarkOccurrence? {
    val list = snapshot ?: manager(bookmark.provider.project)?.snapshot ?: return null
    var index = current
    if (index < 0) index = list.indexOfLast { it.group == group && it.bookmark == bookmark }
    while (true) {
      val occurrence = list.getOrNull(++index) ?: return null
      if (predicate(occurrence)) return occurrence
    }
  }

  fun previousFileBookmark() = previous { it.bookmark is FileBookmark }
  fun previousLineBookmark() = previous { it.bookmark is LineBookmark }
  fun previous(predicate: (BookmarkOccurrence) -> Boolean): BookmarkOccurrence? {
    val list = snapshot ?: manager(bookmark.provider.project)?.snapshot ?: return null
    var index = current
    if (index < 0) index = list.indexOfFirst { it.group == group && it.bookmark == bookmark }
    if (index < 0) index = list.size
    while (true) {
      val occurrence = list.getOrNull(--index) ?: return null
      if (predicate(occurrence)) return occurrence
    }
  }

  override fun hashCode() = Objects.hash(group, bookmark)
  override fun equals(other: Any?) = other === this || other is BookmarkOccurrence
                                     && other.group == group
                                     && other.bookmark == bookmark

  companion object {
    private fun manager(project: Project) = BookmarksManager.getInstance(project) as? BookmarksManagerImpl

    fun firstFileBookmark(project: Project) = first(project) { it.bookmark is FileBookmark }
    fun firstLineBookmark(project: Project) = first(project) { it.bookmark is LineBookmark }
    fun first(project: Project, predicate: (BookmarkOccurrence) -> Boolean) =
      manager(project)?.snapshot?.firstOrNull(predicate)

    fun lastFileBookmark(project: Project) = last(project) { it.bookmark is FileBookmark }
    fun lastLineBookmark(project: Project) = last(project) { it.bookmark is LineBookmark }
    fun last(project: Project, predicate: (BookmarkOccurrence) -> Boolean) =
      manager(project)?.snapshot?.lastOrNull(predicate)
  }
}
