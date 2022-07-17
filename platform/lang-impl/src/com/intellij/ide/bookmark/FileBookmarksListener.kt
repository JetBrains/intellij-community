// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark

import com.intellij.openapi.vfs.VirtualFile
import java.util.function.Consumer

class FileBookmarksListener(private val consumer: Consumer<VirtualFile>) : BookmarksListener {
  override fun bookmarkAdded(group: BookmarkGroup, bookmark: Bookmark) = bookmarkTypeChanged(bookmark)
  override fun bookmarkRemoved(group: BookmarkGroup, bookmark: Bookmark) = bookmarkTypeChanged(bookmark)
  override fun bookmarkChanged(group: BookmarkGroup, bookmark: Bookmark) = bookmarkTypeChanged(bookmark)
  override fun bookmarkTypeChanged(bookmark: Bookmark) {
    (bookmark as? FileBookmark)?.let { consumer.accept(it.file) }
  }
}
