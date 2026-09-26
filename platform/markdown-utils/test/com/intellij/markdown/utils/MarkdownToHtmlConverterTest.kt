// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.markdown.utils

import org.junit.Test
import kotlin.test.assertEquals

class MarkdownToHtmlConverterTest {
  @Test
  fun `default conversion preserves exact GFM output`() {
    val markdownText = "**bold**, *italic*, ~~old~~ and `code`."
    val expected = "<p><strong>bold</strong>, <em>italic</em>, <span class=\"user-del\">old</span> and <code>code</code>.</p>"

    assertEquals(expected, convertMarkdownToHtml(markdownText))
  }

  @Test
  fun `conversion uses an embedded root without body or div wrappers`() {
    assertEquals("<p>text</p>", convertMarkdownToHtml("text"))
    assertEquals("", convertMarkdownToHtml(""))
  }
}