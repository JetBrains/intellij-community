// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.util

import org.junit.Assert.assertEquals
import org.junit.Test

class LinkHttpHeaderValueTest {
  @Test
  fun `test correct header`() {
    val result = LinkHttpHeaderValue.parse(
      """<https://api.github.com/search/code?q=addClass+user%3Amozilla&page=15>; rel="next", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=34>; rel="last", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=1>; rel="first", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=13>; rel="prev"""")

    assertEquals(LinkHttpHeaderValue(
      firstLink = "https://api.github.com/search/code?q=addClass+user%3Amozilla&page=1",
      prevLink = "https://api.github.com/search/code?q=addClass+user%3Amozilla&page=13",
      nextLink = "https://api.github.com/search/code?q=addClass+user%3Amozilla&page=15",
      lastLink = "https://api.github.com/search/code?q=addClass+user%3Amozilla&page=34"
    ), result)
  }

  @Test
  fun `test empty header`() {
    val result = LinkHttpHeaderValue.parse("")

    assertEquals(LinkHttpHeaderValue(), result)
  }

  @Test
  fun `test partial header`() {
    val result = LinkHttpHeaderValue.parse(
      """<https://api.github.com/search/code?q=addClass+user%3Amozilla&page=34>; rel="last", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=1>; rel="first", <https://api.github.com/search/code?q=addClass+user%3Amozilla&page=33>; rel="prev"""")

    assertEquals(LinkHttpHeaderValue(
      firstLink = "https://api.github.com/search/code?q=addClass+user%3Amozilla&page=1",
      prevLink = "https://api.github.com/search/code?q=addClass+user%3Amozilla&page=33",
      lastLink = "https://api.github.com/search/code?q=addClass+user%3Amozilla&page=34",
    ), result)
  }

  @Test
  fun `deprecation is accepted`() {
    val result = LinkHttpHeaderValue.parse(
      """<>; rel="deprecation""""
    )

    assertEquals(LinkHttpHeaderValue(
      isDeprecated = true
    ), result)
  }

  @Test
  fun `deprecation is accepted and type is ignored`() {
    val result = LinkHttpHeaderValue.parse(
      """<>; rel="deprecation"; type="text/html""""
    )

    assertEquals(LinkHttpHeaderValue(
      isDeprecated = true
    ), result)
  }

  @Test
  fun `first rel value is used`() {
    val result = LinkHttpHeaderValue.parse(
      """<bla>; rel="first"; rel="last""""
    )

    assertEquals(LinkHttpHeaderValue(
      firstLink = "bla"
    ), result)
  }

  @Test
  fun `rel value is used also when quotation marks are missing`() {
    val result = LinkHttpHeaderValue.parse(
      """<bla>; rel=first"""
    )

    assertEquals(LinkHttpHeaderValue(
      firstLink = "bla"
    ), result)
  }

  @Test(expected = IllegalStateException::class)
  fun `test incorrect header`() {
    LinkHttpHeaderValue.parse("abirvalg")
  }
}