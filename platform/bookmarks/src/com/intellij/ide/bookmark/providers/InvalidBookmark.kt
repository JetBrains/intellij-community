// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import java.util.*

internal class InvalidBookmark(override val provider: LineBookmarkProvider, val url: String, val line: Int) : Bookmark {

  override val attributes: Map<String, String>
    get() = mapOf("url" to url, "line" to line.toString())

  override fun createNode(): UrlNode = UrlNode(provider.project, this)

  override fun hashCode(): Int = Objects.hash(provider, url, line)
  override fun equals(other: Any?): Boolean = other === this || other is InvalidBookmark
                                              && other.provider == provider
                                              && other.url == url
                                              && other.line == line

  override fun toString(): String = "InvalidBookmark(line=$line,url=$url,provider=$provider)"
}
