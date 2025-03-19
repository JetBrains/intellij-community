// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.bookmarks.backend

import com.intellij.ide.bookmark.BookmarkType
import com.intellij.ide.bookmark.BookmarksManager
import com.intellij.ide.bookmark.providers.LineBookmarkProvider
import com.intellij.openapi.editor.impl.EditorId
import com.intellij.openapi.editor.impl.findEditor
import com.intellij.platform.bookmarks.rpc.BookmarksApi
import com.intellij.platform.project.ProjectId
import com.intellij.platform.project.findProject


internal class BookmarksApiImpl: BookmarksApi {

  override suspend fun addBookmark(projectId: ProjectId, editorId: EditorId, line: Int ) {
    val project = projectId.findProject()
    val editor = editorId.findEditor()
    val manager = BookmarksManager.getInstance(project) ?: return
    val bookmark = LineBookmarkProvider.Util.find(project)?.createBookmark(editor, line) ?: return
    manager.getType(bookmark)?.let { manager.remove(bookmark) } ?: manager.add(bookmark, BookmarkType.DEFAULT)
  }
}