// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinkHttpHeaderValueTest {

  @Test
  fun `test correct header`() {
    val result = LinkHttpHeaderValue.parse(
      """<https://api.github.com/search/code?q=addClass+user%3Amozilla&page=15>; rel="next", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=34>; rel="last", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=1>; rel="first", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=13>; rel="prev"""")

    assertEquals("https://api.github.com/search/code?q=addClass+user%3Amozilla&page=1", result.firstLink)
    assertEquals("https://api.github.com/search/code?q=addClass+user%3Amozilla&page=13", result.prevLink)
    assertEquals("https://api.github.com/search/code?q=addClass+user%3Amozilla&page=15", result.nextLink)
    assertEquals("https://api.github.com/search/code?q=addClass+user%3Amozilla&page=34", result.lastLink)
  }

  @Test
  fun `test empty header`() {
    val result = LinkHttpHeaderValue.parse("")

    assertNull(result.firstLink)
    assertNull(result.prevLink)
    assertNull(result.nextLink)
    assertNull(result.lastLink)
  }

  @Test
  fun `test partial header`() {
    val result = LinkHttpHeaderValue.parse(
      """<https://api.github.com/search/code?q=addClass+user%3Amozilla&page=34>; rel="last", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=1>; rel="first", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=33>; rel="prev"""")

    assertEquals("https://api.github.com/search/code?q=addClass+user%3Amozilla&page=1", result.firstLink)
    assertEquals("https://api.github.com/search/code?q=addClass+user%3Amozilla&page=33", result.prevLink)
    assertNull(result.nextLink)
    assertEquals("https://api.github.com/search/code?q=addClass+user%3Amozilla&page=34", result.lastLink)
  }

  @Test(expected = IllegalStateException::class)
  fun `test incorrect header`() {
    LinkHttpHeaderValue.parse("abirvalg")
  }
}