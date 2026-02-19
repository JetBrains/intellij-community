// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package org.jetbrains.ide

import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

internal class AboutRestApiTest : BuiltInServerTestCase() {
  override val urlPathPrefix = "/api/about"

  @Test
  fun `registeredFileTypes is allowed for local`() {
    doTest("?registeredFileTypes=true", asSignedRequest = false) { response ->
      assertThat(response.body().reader().readText()).contains("registeredFileTypes")
    }
  }

  @Test
  fun `registeredFileTypes is not allowed for non-local origin`() {
    // must be failed with 404
    doTest("?registeredFileTypes=true", origin = "https://jetbrains.com", asSignedRequest = false, responseStatus = 404)
  }

  @Suppress("SpellCheckingInspection")
  @Test
  fun `registeredFileTypes is not allowed for hyperskill origin`() {
    // must be failed with 404
    doTest("?registeredFileTypes=true", origin = "https://subdomain.hyperskill.org", asSignedRequest = false) { response ->
      assertThat(response.body().reader().readText()).doesNotContain("registeredFileTypes")
    }
  }

  @Test
  fun `about is not allowed for almost hyperskill origin`() {
    doTest("", origin = "https://hyperskillAorg", asSignedRequest = false, responseStatus = 404)
  }

  @Test
  fun `about is not allowed for almost jetbrains origin`() {
    doTest("", origin = "https://jetbrainsAcom", asSignedRequest = false, responseStatus = 404)
  }
}