// Copyright 2000-2021 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import java.util.Objects

internal class InvalidBookmark(override val provider: LineBookmarkProvider, val url: String, val line: Int) : Bookmark {

  override val attributes: Map<String, String>
    get() = mapOf("url" to url, "line" to line.toString())

  override fun createNode() = UrlNode(provider.project, this)

  override fun canNavigate() = false
  override fun canNavigateToSource() = false
  override fun navigate(requestFocus: Boolean) = Unit

  override fun hashCode() = Objects.hash(provider, url, line)
  override fun equals(other: Any?) = other === this || other is InvalidBookmark
                                     && other.provider == provider
                                     && other.url == url
                                     && other.line == line

  override fun toString() = "InvalidBookmark(line=$line,url=$url,provider=$provider)"
}
