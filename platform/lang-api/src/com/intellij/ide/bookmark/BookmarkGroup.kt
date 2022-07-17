// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.openapi.util.NlsSafe

interface BookmarkGroup {
  var name: @NlsSafe String
  var isDefault: Boolean

  /**
   * @return a list of contained bookmarks
   */
  fun getBookmarks(): List<Bookmark>

  /**
   * @return a bookmark description or `null` if the bookmark is not contained in this group
   */
  fun getDescription(bookmark: Bookmark): @NlsSafe String?
  fun setDescription(bookmark: Bookmark, description: @NlsSafe String)

  fun canAdd(bookmark: Bookmark): Boolean
  fun add(bookmark: Bookmark, type: BookmarkType, description: @NlsSafe String? = null): Boolean

  fun canRemove(bookmark: Bookmark): Boolean
  fun remove(bookmark: Bookmark): Boolean

  fun remove()
}
