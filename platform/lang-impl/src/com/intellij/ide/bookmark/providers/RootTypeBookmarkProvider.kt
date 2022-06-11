// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import com.intellij.ide.bookmark.BookmarkProvider
import com.intellij.ide.scratch.RootType
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil

internal class RootTypeBookmarkProvider(private val project: Project) : BookmarkProvider {
  override fun getWeight() = 150
  override fun getProject() = project

  override fun compare(bookmark1: Bookmark, bookmark2: Bookmark): Int {
    bookmark1 as RootTypeBookmark
    bookmark2 as RootTypeBookmark
    return StringUtil.naturalCompare(bookmark1.type.id, bookmark2.type.id)
  }

  override fun createBookmark(map: MutableMap<String, String>): RootTypeBookmark? {
    val id = map["root.type.id"] ?: return null
    return createBookmark(RootType.getAllRootTypes().firstOrNull { it.id == id })
  }

  override fun createBookmark(context: Any?) = when (context) {
    is RootType -> RootTypeBookmark(this, context)
    else -> null
  }
}
