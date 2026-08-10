// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.openapi.util.registry.RegistryManager
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.net.ProxyConfiguration
import com.intellij.util.net.ProxySettings
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import java.net.InetSocketAddress
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference
import kotlin.time.Duration.Companion.milliseconds

private const val MAX_SCHEMA_BYTES_KEY = "json.schema.remote.http.max.schema.bytes"
private const val MAX_REDIRECTS_KEY = "json.schema.remote.http.max.redirects"

class JsonSchemaHttpClientTest : BasePlatformTestCase() {
  private lateinit var server: HttpServer
  private lateinit var baseUrl: String

  override fun setUp() {
    super.setUp()
    server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
    server.executor = null
    server.start()
    baseUrl = "http://127.0.0.1:${server.address.port}"
  }

  override fun tearDown() {
    try {
      server.stop(0)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun testDownloadsHttpSchema() = runBlocking {
    server.createContext("/schema") { exchange ->
      val bytes = "schema".toByteArray()
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    JsonSchemaHttpClient().use { client ->
      val result = client.download(SchemaUrl.parse("$baseUrl/schema")) as DownloadResult.Success
      assertEquals("schema", String(result.content.bytes))
    }
  }

  fun testFailsForSuccessfulStatusOtherThan200() = runBlocking {
    server.createContext("/created") { exchange ->
      exchange.sendResponseHeaders(201, -1)
      exchange.close()
    }
    withRegistryValues("json.schema.remote.http.max.retries" to 1) {
      JsonSchemaHttpClient().use { client ->
        assertTrue(client.download(SchemaUrl.parse("$baseUrl/created")) is DownloadResult.Failed)
      }
    }
  }

  fun testConditionalRefreshSendsValidatorsAndAcceptsNotModified() = runBlocking {
    server.createContext("/schema") { exchange ->
      assertEquals("etag", exchange.requestHeaders.getFirst("If-None-Match"))
      assertEquals("yesterday", exchange.requestHeaders.getFirst("If-Modified-Since"))
      exchange.sendResponseHeaders(304, -1)
      exchange.close()
    }
    JsonSchemaHttpClient().use { client ->
      assertSame(
        DownloadResult.NotModified,
        client.refresh(SchemaUrl.parse("$baseUrl/schema"), "etag", "yesterday"),
      )
    }
  }

  fun testRejectsNonHttpSchemes() = runBlocking {
    JsonSchemaHttpClient().use { client ->
      assertTrue(client.download(SchemaUrl.parse("file:///tmp/schema.json")) is DownloadResult.Rejected)
      assertTrue(client.download(SchemaUrl.parse("https://user@example.com/schema.json")) is DownloadResult.Rejected)
    }
  }

  fun testRedirectToFileIsCacheableError() = runBlocking {
    server.createContext("/redirect") { exchange ->
      exchange.responseHeaders.add("Location", "file:///tmp/schema.json")
      exchange.sendResponseHeaders(302, -1)
      exchange.close()
    }
    JsonSchemaHttpClient().use { client ->
      assertEquals(
        DownloadResult.Error.FORBIDDEN_REDIRECT_TARGET,
        client.download(SchemaUrl.parse("$baseUrl/redirect")),
      )
    }
  }

  fun testRedirectWithMalformedLocationIsCacheableError() = runBlocking {
    server.createContext("/bad-redirect") { exchange ->
      exchange.responseHeaders.add("Location", "http://example.com/inva lid path")
      exchange.sendResponseHeaders(302, -1)
      exchange.close()
    }
    JsonSchemaHttpClient().use { client ->
      assertEquals(
        DownloadResult.Error.INVALID_REDIRECT_TARGET,
        client.download(SchemaUrl.parse("$baseUrl/bad-redirect")),
      )
    }
  }

  fun testStopsAfterConfiguredRedirectLimit() = runBlocking {
    repeat(7) { index ->
      server.createContext("/redirect$index") { exchange ->
        exchange.responseHeaders.add("Location", "$baseUrl/redirect${index + 1}")
        exchange.sendResponseHeaders(302, -1)
        exchange.close()
      }
    }
    withRegistryValues(MAX_REDIRECTS_KEY to 5) {
      JsonSchemaHttpClient().use { client ->
        assertEquals(
          DownloadResult.Error.REDIRECT_LIMIT_EXCEEDED,
          client.download(SchemaUrl.parse("$baseUrl/redirect0")),
        )
      }
    }
  }

  fun testResponseOverConfiguredLimitIsRejected() = runBlocking {
    withRegistryValues(MAX_SCHEMA_BYTES_KEY to 1) {
      server.createContext("/large") { exchange ->
        val size = 2
        exchange.sendResponseHeaders(200, size.toLong())
        exchange.responseBody.use { it.write(ByteArray(size)) }
      }
      JsonSchemaHttpClient().use { client ->
        assertEquals(
          DownloadResult.Error.OVERSIZED_CONTENT,
          client.download(SchemaUrl.parse("$baseUrl/large")),
        )
      }
    }
  }

  fun testChunkedResponseOverConfiguredLimitIsRejected() = runBlocking {
    withRegistryValues(MAX_SCHEMA_BYTES_KEY to 1) {
      server.createContext("/large-chunked") { exchange ->
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.use { it.write(ByteArray(2)) }
      }
      JsonSchemaHttpClient().use { client ->
        assertEquals(
          DownloadResult.Error.OVERSIZED_CONTENT,
          client.download(SchemaUrl.parse("$baseUrl/large-chunked")),
        )
      }
    }
  }

  fun testRequestTimesOut() = runBlocking {
    server.createContext("/slow") { exchange ->
      Thread.sleep(1_000)
      exchange.sendResponseHeaders(200, 0)
      exchange.responseBody.close()
    }
    withRegistryValues(
      "json.schema.remote.http.timeout" to 100,
      "json.schema.remote.http.max.retries" to 1,
    ) {
      JsonSchemaHttpClient().use { client ->
        assertTrue(client.download(SchemaUrl.parse("$baseUrl/slow")) is DownloadResult.Failed)
      }
    }
  }

  fun testDownloadCapturesEtagAndLastModifiedHeaders() = runBlocking {
    server.createContext("/schema") { exchange ->
      exchange.responseHeaders.add("ETag", "\"v1\"")
      exchange.responseHeaders.add("Last-Modified", "Mon, 01 Jan 2024 00:00:00 GMT")
      val bytes = "schema".toByteArray()
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    JsonSchemaHttpClient().use { client ->
      val result = client.download(SchemaUrl.parse("$baseUrl/schema")) as DownloadResult.Success
      assertEquals("\"v1\"", result.etag)
      assertEquals("Mon, 01 Jan 2024 00:00:00 GMT", result.lastModified)
    }
  }

  fun testDownloadWithoutCacheHeadersReturnsNullEtagAndLastModified() = runBlocking {
    server.createContext("/schema") { exchange ->
      val bytes = "schema".toByteArray()
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    JsonSchemaHttpClient().use { client ->
      val result = client.download(SchemaUrl.parse("$baseUrl/schema")) as DownloadResult.Success
      assertNull(result.etag)
      assertNull(result.lastModified)
    }
  }

  fun testDownload404ReturnsCacheableError() = runBlocking {
    server.createContext("/missing") { exchange ->
      exchange.sendResponseHeaders(404, -1)
      exchange.close()
    }
    JsonSchemaHttpClient().use { client ->
      assertEquals(
        DownloadResult.Error(404, "HTTP 404"),
        client.download(SchemaUrl.parse("$baseUrl/missing")),
      )
    }
  }

  fun testClientErrorStatusIsCacheableError() = runBlocking {
    server.createContext("/forbidden") { exchange ->
      exchange.sendResponseHeaders(403, -1)
      exchange.close()
    }
    JsonSchemaHttpClient().use { client ->
      assertEquals(
        DownloadResult.Error(403, "HTTP 403"),
        client.download(SchemaUrl.parse("$baseUrl/forbidden")),
      )
    }
  }

  fun testRetriesUpToConfiguredAttemptsOn500ThenFails() = runBlocking {
    val callCount = AtomicInteger()
    server.createContext("/flaky") { exchange ->
      callCount.incrementAndGet()
      exchange.sendResponseHeaders(500, -1)
      exchange.close()
    }
    withRegistryValues(
      "json.schema.remote.http.max.retries" to 3,
      "json.schema.remote.http.backoff.step" to 10,
    ) {
      JsonSchemaHttpClient().use { client ->
        assertTrue(client.download(SchemaUrl.parse("$baseUrl/flaky")) is DownloadResult.Failed)
      }
    }
    assertEquals(3, callCount.get())
  }

  fun testConditionalRefreshReturnsFreshContentWhenServerReturns200() = runBlocking {
    server.createContext("/schema") { exchange ->
      val bytes = "updated".toByteArray()
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    JsonSchemaHttpClient().use { client ->
      val result = client.refresh(SchemaUrl.parse("$baseUrl/schema"), "old-etag", "yesterday")
      assertTrue(result is DownloadResult.Success)
      assertEquals("updated", String((result as DownloadResult.Success).content.bytes))
    }
  }

  fun testUnreachableHostReturnsFailed() = runBlocking {
    withRegistryValues("json.schema.remote.http.max.retries" to 1) {
      JsonSchemaHttpClient().use { client ->
        assertTrue(client.download(SchemaUrl.parse("http://127.0.0.1:1/schema.json")) is DownloadResult.Failed)
      }
    }
  }

  fun testUnresolvedHostReturnsFailed() = runBlocking {
    withRegistryValues("json.schema.remote.http.max.retries" to 1) {
      JsonSchemaHttpClient().use { client ->
        // The .invalid top-level domain never resolves (RFC 2606).
        assertTrue(client.download(SchemaUrl.parse("http://schema.invalid/schema.json")) is DownloadResult.Failed)
      }
    }
  }

  fun testDownloadUsesTheIdeProxy() = runBlocking {
    val requestedUri = AtomicReference<String>()
    server.createContext("/") { exchange ->
      requestedUri.set(exchange.requestURI.toString())
      val bytes = "proxied".toByteArray()
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    val settings = ProxySettings.getInstance()
    val previous = settings.getProxyConfiguration()
    settings.setProxyConfiguration(ProxyConfiguration.proxy(ProxyConfiguration.ProxyProtocol.HTTP, "127.0.0.1", server.address.port))
    try {
      JsonSchemaHttpClient().use { client ->
        val result = client.download(SchemaUrl.parse("http://schema.example.test/proxied.json")) as DownloadResult.Success
        assertEquals("proxied", String(result.content.bytes))
      }
    }
    finally {
      settings.setProxyConfiguration(previous)
    }
    assertEquals("the request must go through the configured proxy", "http://schema.example.test/proxied.json", requestedUri.get())
  }

  fun testDownloadPreservesBinaryContent() = runBlocking {
    val binary = byteArrayOf(0x00, 0x01, 0xFF.toByte(), 0x7F, 0x80.toByte(), 0x0A, 0x0D)
    server.createContext("/binary") { exchange ->
      exchange.sendResponseHeaders(200, binary.size.toLong())
      exchange.responseBody.use { it.write(binary) }
    }
    JsonSchemaHttpClient().use { client ->
      val result = client.download(SchemaUrl.parse("$baseUrl/binary")) as DownloadResult.Success
      assertTrue(result.content.bytes.contentEquals(binary))
    }
  }

  fun testSuccessReportsTheFinalUrlAfterRedirect() = runBlocking {
    server.createContext("/root.json") { exchange ->
      exchange.responseHeaders.add("Location", "$baseUrl/schemas/root.yaml")
      exchange.sendResponseHeaders(302, -1)
      exchange.close()
    }
    server.createContext("/schemas/root.yaml") { exchange ->
      val bytes = "type: object".toByteArray()
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    JsonSchemaHttpClient().use { client ->
      val success = client.download(SchemaUrl.parse("$baseUrl/root.json")) as DownloadResult.Success
      assertEquals(SchemaUrl.parse("$baseUrl/schemas/root.yaml").value, success.finalUrl)
    }
  }

  fun testSuccessWithoutRedirectReportsTheRequestedUrl() = runBlocking {
    server.createContext("/root.json") { exchange ->
      val bytes = "{}".toByteArray()
      exchange.sendResponseHeaders(200, bytes.size.toLong())
      exchange.responseBody.use { it.write(bytes) }
    }
    JsonSchemaHttpClient().use { client ->
      val success = client.download(SchemaUrl.parse("$baseUrl/root.json")) as DownloadResult.Success
      assertEquals(SchemaUrl.parse("$baseUrl/root.json").value, success.finalUrl)
    }
  }

  fun testCancellationStopsRead() = runBlocking {
    val started = AtomicInteger()
    server.createContext("/stream") { exchange ->
      exchange.use {
        exchange.sendResponseHeaders(200, 0)
        repeat(1_000) {
          started.incrementAndGet()
          exchange.responseBody.write(ByteArray(1024))
          exchange.responseBody.flush()
          Thread.sleep(10)
        }
      }
    }
    JsonSchemaHttpClient().use { client ->
      val job = async(Dispatchers.IO) { client.download(SchemaUrl.parse("$baseUrl/stream")) }
      while (started.get() == 0) delay(10.milliseconds)
      job.cancelAndJoin()
      assertTrue(job.isCancelled)
    }
  }

  private suspend fun <T> withRegistryValues(vararg values: Pair<String, Int>, action: suspend () -> T): T {
    val registry = RegistryManager.getInstance()
    val previousValues = values.associate { (key, _) -> key to registry.get(key).asString() }
    values.forEach { (key, value) -> registry.get(key).setValue(value) }
    try {
      return action()
    }
    finally {
      previousValues.forEach { (key, value) -> registry.get(key).setValue(value) }
    }
  }
}
