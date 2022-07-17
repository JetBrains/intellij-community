// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.LineBookmark
import com.intellij.ide.bookmark.ui.tree.LineNode
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.vfs.VirtualFile
import java.util.*

class LineBookmarkImpl(override val provider: LineBookmarkProvider, file: VirtualFile, line: Int) : LineBookmark {
  val descriptor = OpenFileDescriptor(provider.project, file, line, 0)

  override val file
    get() = descriptor.file

  override val line
    get() = descriptor.line

  override val attributes: Map<String, String>
    get() = mapOf("url" to file.url, "line" to line.toString())

  override fun createNode() = LineNode(provider.project, this)

  override fun canNavigate() = !provider.project.isDisposed && descriptor.canNavigate()
  override fun canNavigateToSource() = !file.isDirectory && canNavigate()
  override fun navigate(requestFocus: Boolean) = descriptor.navigate(requestFocus)

  override fun hashCode() = Objects.hash(provider, file, line)
  override fun equals(other: Any?) = other === this || other is LineBookmarkImpl
                                     && other.provider == provider
                                     && other.file == file
                                     && other.line == line

  override fun toString() = "LineBookmarkImpl(line=$line,file=$file,provider=$provider)"
}
