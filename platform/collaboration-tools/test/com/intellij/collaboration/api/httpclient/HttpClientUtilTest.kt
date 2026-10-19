// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.collaboration.api.httpclient

import com.intellij.openapi.diagnostic.Logger
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.InputStream
import java.net.http.HttpHeaders
import java.net.http.HttpResponse

internal class HttpClientUtilTest {
  @Test
  fun `body reader receives the full response without trace logging`() {
    val expectedBody = "response body"
    val response = createResponse(expectedBody)
    val logger = mockk<Logger>(relaxed = true) {
      every { isTraceEnabled } returns false
    }

    val body = response.readBodyWithLoggingBlocking(logger, "test request") { _, _ -> reader().readText() }

    assertThat(body).isEqualTo(expectedBody)
    verify(exactly = 0) { logger.trace(any<String>()) }
  }

  @Test
  fun `body reader receives the full response with trace logging`() {
    val expectedBody = "response body"
    val response = createResponse(expectedBody)
    val logger = mockk<Logger>(relaxed = true) {
      every { isTraceEnabled } returns true
    }

    val body = response.readBodyWithLoggingBlocking(logger, "test request") { _, _ -> reader().readText() }

    assertThat(body).isEqualTo(expectedBody)
    verify(exactly = 1) { logger.trace("test request : Response body: $expectedBody") }
  }

  private fun createResponse(body: String): HttpResponse<InputStream> {
    val responseHeaders = HttpHeaders.of(
      mapOf("Content-Type" to listOf("text/plain"))
    ) { _, _ -> true }
    val responseStream = body.byteInputStream()
    return mockk {
      every { headers() } returns responseHeaders
      every { body() } returns responseStream
    }
  }
}