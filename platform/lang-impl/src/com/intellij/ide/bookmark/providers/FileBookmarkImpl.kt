// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.FileBookmark
import com.intellij.ide.bookmark.ui.tree.BookmarkNode
import com.intellij.ide.bookmark.ui.tree.FileNode
import com.intellij.ide.bookmark.ui.tree.FolderNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

class FileBookmarkImpl(override val provider: LineBookmarkProvider, file: VirtualFile) : FileBookmark {
  val descriptor: OpenFileDescriptor = OpenFileDescriptor(provider.project, file)

  override val file: VirtualFile
    get() = descriptor.file

  override val attributes: Map<String, String>
    get() = mapOf("url" to file.url)

  override fun createNode(): BookmarkNode<FileBookmark> = when (file.isDirectory) {
    true -> FolderNode(provider.project, this)
    else -> FileNode(provider.project, this)
  }

  override fun canNavigate(): Boolean = !provider.project.isDisposed && descriptor.canNavigate()
  override fun canNavigateToSource(): Boolean = !file.isDirectory && canNavigate()
  override fun navigate(requestFocus: Boolean): Unit = descriptor.navigate(requestFocus)

  override fun hashCode(): Int = Objects.hash(provider, file)
  override fun equals(other: Any?): Boolean = other === this || other is FileBookmarkImpl
                                              && other.provider == provider
                                              && other.file == file

  override fun toString(): String = "FileBookmarkImpl(file=$file,provider=$provider)"
}
