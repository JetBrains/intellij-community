// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.favoritesTreeView

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkProvider
import com.intellij.openapi.project.Project
import java.lang.Deprecated

@Deprecated(forRemoval = true)
internal class AbstractUrlBookmarkProvider(private val project: Project) : BookmarkProvider {
  override fun getWeight(): Int = Int.MAX_VALUE
  override fun getProject(): Project = project

  override fun compare(bookmark1: Bookmark?, bookmark2: Bookmark?): Int = 0

  override fun createBookmark(map: Map<String, String>): Bookmark? = null

  override fun createBookmark(context: Any?): Bookmark? = when (context) {
    is AbstractUrlFavoriteAdapter -> context.createBookmark(project)
    else -> null
  }
}
