// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.ide

import com.intellij.testFramework.junit5.TestApplication
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

@TestApplication
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
