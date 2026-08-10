// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.testFramework.replaceService
import com.intellij.util.WaitFor
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonSchemaServiceImpl
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory
import com.jetbrains.jsonSchema.extension.SchemaType
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.net.InetSocketAddress
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.time.Duration.Companion.hours

/**
 * Integration coverage for the full pipeline that [JsonFileResolver.resolveSchemaByReference] drives
 * through the registered [JsonSchemaFileCache] and [JsonSchemaHttpClient] services talking to
 * an embedded [HttpServer], instead of the [SchemaContentCache]/[SchemaHttpTransport] fakes used by
 * [JsonSchemaRemoteContentServiceTest] and [com.jetbrains.jsonSchema.remote.JsonFileResolverTest].
 */
@TestApplication
class JsonSchemaRemoteHandlerIntegrationTest {
  private companion object {
    val projectFixture = projectFixture()
  }

  private lateinit var scope: CoroutineScope
  private lateinit var httpServer: HttpServer
  private lateinit var baseUrl: String

  private val requestCounts = ConcurrentHashMap<String, AtomicInteger>()
  private val httpHandlers = mutableMapOf<String, (HttpExchange) -> Unit>()

  @BeforeEach
  fun setUp() {
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    clearRemoteSchemaCache()

    httpServer = HttpServer.create(InetSocketAddress(0), 0)
    httpServer.executor = Executors.newCachedThreadPool()
    httpServer.createContext("/") { exchange ->
      requestCounts.computeIfAbsent(exchange.requestURI.path) { AtomicInteger(0) }.incrementAndGet()
      val handler = httpHandlers[exchange.requestURI.path]
      if (handler != null) handler(exchange) else sendResponse(exchange, 404, "Not Found", emptyMap())
    }
    httpServer.start()
    baseUrl = "http://localhost:${httpServer.address.port}"
  }

  @AfterEach
  fun tearDown() {
    httpServer.stop(0)
    scope.cancel()
    clearRemoteSchemaCache()
  }

  @Test
  fun `resolveSchemaByReference downloads via real HTTP and returns a real cached local file`(@TestDisposable disposable: Disposable) {
    installService(disposable)
    val path = "/pipeline-schema.json"
    val body = """{"type":"object"}"""
    httpHandlers[path] = { exchange -> sendResponse(exchange, 200, body, mapOf("ETag" to "\"v1\"")) }
    val url = "$baseUrl$path"

    val started = System.nanoTime()
    val first = JsonFileResolver.resolveSchemaByReference(null, url, projectFixture.get())
    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

    assertNull(first, "A cold miss must return null until the remote schema is cached")
    assertTrue(elapsedMs < 1_000, "Cold resolution must not wait for the remote download")
    assertTrue(
      waitFor(5_000) { service().getCachedFile(url) != null },
      "The first lookup must prefetch and the second cache lookup must observe the local file",
    )

    val file = JsonFileResolver.resolveSchemaByReference(null, url, projectFixture.get())

    assertNotNull(file, "Expected a local VirtualFile after a successful real HTTP download")
    assertEquals(body, file!!.contentsToByteArray().toString(Charsets.UTF_8))
    assertEquals(1, requestCount(path))
  }

  @Test
  fun `second resolution reuses the real file cache without another HTTP request`(@TestDisposable disposable: Disposable) {
    installService(disposable)
    val path = "/dedup-schema.json"
    val body = """{"type":"string"}"""
    httpHandlers[path] = { exchange -> sendResponse(exchange, 200, body, mapOf("ETag" to "\"e1\"")) }
    val url = "$baseUrl$path"
    assertNotNull(cacheFile(url))

    val first = JsonFileResolver.resolveSchemaByReference(null, url, projectFixture.get())
    assertNotNull(first, "First resolution must succeed")
    assertEquals(1, requestCount(path))

    val second = JsonFileResolver.resolveSchemaByReference(null, url, projectFixture.get())
    assertNotNull(second, "Second resolution must succeed")
    assertEquals(body, second!!.contentsToByteArray().toString(Charsets.UTF_8))
    assertEquals(1, requestCount(path), "The real file cache must serve the second resolution without another HTTP request")
  }

  @Test
  fun `404 response is cached as not-found and a repeated resolution skips HTTP entirely`(@TestDisposable disposable: Disposable) {
    installService(disposable)
    val path = "/will-404.json"
    httpHandlers[path] = { exchange -> sendResponse(exchange, 404, "Not Found", emptyMap()) }
    val url = "$baseUrl$path"

    val first = JsonFileResolver.resolveSchemaByReference(null, url, projectFixture.get())
    assertNull(first, "A 404 cache miss must return null")

    val cached = runOnPooledThread { service().downloadAndWaitForTest(SchemaUrl.parse(url)) }
    assertNull(cached, "A 404 response must resolve to no cached file")
    assertEquals(1, requestCount(path))

    val second = JsonFileResolver.resolveSchemaByReference(null, url, projectFixture.get())
    assertNull(second, "A cached 404 must keep resolving to null")
    assertEquals(1, requestCount(path), "A cached 404 must not be re-fetched")
  }

  @Test
  fun `stale cache entry is revalidated via conditional GET, a 304 keeps content and refreshes staleness`(
    @TestDisposable disposable: Disposable,
  ) {
    installService(disposable)
    val path = "/etag-schema.json"
    val body = """{"original":true}"""
    httpHandlers[path] = { exchange ->
      val ifNoneMatch = exchange.requestHeaders.getFirst("If-None-Match")
      if (ifNoneMatch == "\"v1\"") sendResponse(exchange, 304, "", emptyMap())
      else sendResponse(exchange, 200, body, mapOf("ETag" to "\"v1\""))
    }
    val schemaUrl = SchemaUrl.parse("$baseUrl$path")

    fileCache.write(schemaUrl, DownloadResult.Success(SchemaContent(body.toByteArray(Charsets.UTF_8)), "\"v1\"", null, schemaUrl.value))
    ageRemoteSchemaCache(5.hours)
    fileCache.load(schemaUrl)
    assertTrue((fileCache.read(schemaUrl) as CachedSchema.Content).isStale, "Entry aged by 5 hours must read as stale")

    assertNotNull(cacheFile("$baseUrl$path"))
    val file = JsonFileResolver.resolveSchemaByReference(null, "$baseUrl$path", projectFixture.get())

    assertNotNull(file, "A stale entry revalidated via 304 must still resolve to the cached file")
    assertEquals(body, file!!.contentsToByteArray().toString(Charsets.UTF_8))
    assertTrue(waitFor(5_000) { !(fileCache.read(schemaUrl) as CachedSchema.Content).isStale }, "304 revalidation must refresh the staleness timestamp")
    assertEquals(1, requestCount(path), "Revalidation must be a single conditional GET, not a full re-download")
  }

  @Test
  fun `concurrent resolutions for an uncached URL hit the real HTTP server exactly once`(@TestDisposable disposable: Disposable) {
    installService(disposable)
    val path = "/concurrent-schema.json"
    val body = """{"concurrent":true}"""
    httpHandlers[path] = { exchange ->
      Thread.sleep(20)
      sendResponse(exchange, 200, body, mapOf("ETag" to "\"c1\""))
    }
    val url = "$baseUrl$path"
    assertNotNull(cacheFile(url))

    val executor = Executors.newFixedThreadPool(8)
    try {
      val results = (1..8).map {
        executor.submit<VirtualFile?> { JsonFileResolver.resolveSchemaByReference(null, url, projectFixture.get()) }
      }.map { it.get(10, TimeUnit.SECONDS) }

      results.forEach { file ->
        assertNotNull(file, "Every concurrent caller must receive a resolved file")
        assertEquals(body, file!!.contentsToByteArray().toString(Charsets.UTF_8))
      }
      assertEquals(1, requestCount(path), "Deduplication must reduce concurrent real HTTP calls to 1")
    }
    finally {
      executor.shutdown()
    }
  }

  @Test
  fun `remote activity disabled resolves to no file without any real HTTP call`(@TestDisposable disposable: Disposable) {
    installService(disposable, remoteAccessAllowed = false)
    val path = "/remote-disabled-schema.json"
    httpHandlers[path] = { exchange -> sendResponse(exchange, 200, "{}", emptyMap()) }

    val file = JsonFileResolver.resolveSchemaByReference(null, "$baseUrl$path", projectFixture.get())

    assertNull(file, "Disabled remote access must resolve to null")
    assertEquals(0, requestCount(path), "The HTTP server must not be contacted when remote activity is disabled")
  }

  @Test
  fun `getSchemaObject reads a cached local file`(@TestDisposable disposable: Disposable) {
    installService(disposable)
    val path = "/implicit-schema.json"
    val body = """{"title":"implicit-cached","type":"object"}"""
    httpHandlers[path] = { exchange -> sendResponse(exchange, 200, body, mapOf("ETag" to "\"i1\"")) }
    val url = "$baseUrl$path"
    assertNotNull(cacheFile(url))
    val requestsAfterWarmup = requestCount(path)

    val cachedFile = cacheFile(url)
    assertNotNull(cachedFile)

    val schema = JsonCachedValues.getSchemaObject(cachedFile!!, projectFixture.get())

    assertNotNull(schema, "A cache hit must compile the local file")
    assertEquals("implicit-cached", schema!!.title)
    assertEquals(requestsAfterWarmup, requestCount(path), "Remapping a cache hit must not download again")
  }

  @Test
  fun `remote-only provider resolves to a cached local file`(@TestDisposable disposable: Disposable) {
    installService(disposable)
    val path = "/provider-schema.json"
    val body = """{"title":"provider-cached","type":"object"}"""
    httpHandlers[path] = { exchange -> sendResponse(exchange, 200, body, emptyMap()) }
    val url = "$baseUrl$path"
    val provider = object : JsonSchemaFileProvider {
      override fun isAvailable(file: VirtualFile): Boolean = true
      override fun getName(): String = "provider-schema"
      override fun getSchemaFile(): VirtualFile? = null
      override fun getSchemaType(): SchemaType = SchemaType.remoteSchema
      override fun getRemoteSource(): String = url
    }
    val factory = object : JsonSchemaProviderFactory, DumbAware {
      override fun getProviders(project: Project): List<JsonSchemaFileProvider> = listOf(provider)
    }
    val schemaService = object : JsonSchemaServiceImpl(projectFixture.get()) {
      override fun getProviderFactories(): List<JsonSchemaProviderFactory> = listOf(factory)
    }
    projectFixture.get().replaceService(JsonSchemaService::class.java, schemaService, disposable)

    val targetFile = LightVirtualFile("provider-target.json", "{}")
    assertTrue(runReadActionBlocking { schemaService.getSchemasForFile(targetFile, true, false) }.isEmpty())
    assertTrue(waitFor(5_000) { service().getCachedFile(url) != null })

    val schemaFiles = runReadActionBlocking { schemaService.getSchemasForFile(targetFile, true, false) }
    assertEquals(1, schemaFiles.size)
    assertEquals("provider-cached", runReadActionBlocking { schemaService.getSchemaObject(targetFile) }!!.title)
  }

  private fun installService(disposable: Disposable, remoteAccessAllowed: Boolean = true) {
    setRemoteSchemaAccessAllowed(remoteAccessAllowed, disposable)
    replaceRemoteSchemaServices(projectFixture.get(), scope, disposable)
  }

  private val fileCache: SchemaContentCache get() = ApplicationManager.getApplication().service()

  private fun service(): JsonSchemaRemoteContentService = JsonSchemaRemoteContentService.getInstance(projectFixture.get())

  private fun <T> runOnPooledThread(action: () -> T): T {
    val executor = Executors.newSingleThreadExecutor()
    return try {
      executor.submit<T> { action() }.get(10, TimeUnit.SECONDS)
    }
    finally {
      executor.shutdownNow()
    }
  }

  private fun cacheFile(url: String): VirtualFile? =
    runOnPooledThread { service().downloadAndWaitForTest(SchemaUrl.parse(url)) }

  private fun waitFor(timeoutMs: Int, predicate: () -> Boolean): Boolean =
    object : WaitFor(timeoutMs) {
      override fun condition(): Boolean = predicate()
    }.isConditionRealized

  private fun sendResponse(exchange: HttpExchange, statusCode: Int, body: String, headers: Map<String, String>) {
    headers.forEach { (k, v) -> exchange.responseHeaders.add(k, v) }
    val bytes = body.toByteArray(Charsets.UTF_8)
    exchange.sendResponseHeaders(statusCode, bytes.size.toLong())
    exchange.responseBody.use { it.write(bytes) }
  }

  private fun requestCount(path: String) = requestCounts[path]?.get() ?: 0
}
