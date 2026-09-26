// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.ide.bookmark.providers

import com.intellij.ide.bookmark.Bookmark
import java.util.Objects

internal class InvalidBookmark(
  override val provider: LineBookmarkProvider,
  val url: String,
  val line: Int,
  val expectedText: String? = null
) : Bookmark {

  override val attributes: Map<String, String>
    get() = buildMap {
      put("url", url)
      put("line", line.toString())
      expectedText?.let { put("lineText", it) }
    }

  override fun createNode(): UrlNode = UrlNode(provider.project, this)

  override fun hashCode(): Int = Objects.hash(provider, url, line, expectedText)
  override fun equals(other: Any?): Boolean = other === this || other is InvalidBookmark
                                              && other.provider == provider
                                              && other.url == url
                                              && other.line == line
                                              && other.expectedText == expectedText

  override fun toString(): String = "InvalidBookmark(line=$line,url=$url,expectedText=$expectedText,provider=$provider)"
}
