// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.ui

import com.intellij.ide.bookmark.*

internal class GroupBookmarkSnapshot(val group: BookmarkGroup, val bookmark: Bookmark) {
  private val list: List<Pair<BookmarkGroup, Bookmark>> = manager?.snapshot ?: emptyList()
  private val index: Int = list.indexOfFirst { it.first === group && it.second === bookmark }
  private val manager
    get() = BookmarksManager.getInstance(bookmark.provider.project) as? BookmarksManagerImpl

  val next
    get() = next(false)

  val previous
    get() = next(true)

  private fun next(previous: Boolean): Pair<BookmarkGroup, Bookmark>? {
    var index = if (index >= 0) index else if (previous) list.size else -1
    while (true) {
      index += if (previous) -1 else 1
      val pair = list.getOrNull(index) ?: return null
      if (pair.second is LineBookmark) return pair
    }
  }
}
