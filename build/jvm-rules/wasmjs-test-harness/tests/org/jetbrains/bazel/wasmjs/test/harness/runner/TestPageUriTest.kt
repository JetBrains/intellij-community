// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.bazel.wasmjs.test.harness.runner

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI

class TestPageUriTest {
  @Test
  fun `no filters means the plain index page`() {
    assertEquals(URI("http://127.0.0.1:1234/index.html"), testPageUri(URI("http://127.0.0.1:1234/"), emptyList()))
  }

  @Test
  fun `filters become repeated include query parameters`() {
    assertEquals(
      URI("http://127.0.0.1:1234/index.html?include=a.B.c&include=d.E.*"),
      testPageUri(URI("http://127.0.0.1:1234/"), listOf("a.B.c", "d.E.*")),
    )
  }

  @Test
  fun `filter characters are encoded the way URLSearchParams decodes them`() {
    // URLEncoder emits '+' for a space and %26 for '&'; URLSearchParams.getAll decodes both back.
    assertEquals(
      "include=a+b%26c",
      testPageUri(URI("http://127.0.0.1:1234/"), listOf("a b&c")).rawQuery,
    )
  }
}
