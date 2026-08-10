// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.WriteAction
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.ex.temp.TempFileSystem
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.remote.http.CachedSchema
import com.jetbrains.jsonSchema.remote.http.DownloadResult
import com.jetbrains.jsonSchema.remote.http.SchemaContent
import com.jetbrains.jsonSchema.remote.http.SchemaContentCache
import com.jetbrains.jsonSchema.remote.http.SchemaHttpTransport
import com.jetbrains.jsonSchema.remote.http.SchemaUrl
import com.jetbrains.jsonSchema.remote.http.replaceRemoteSchemaServices
import com.jetbrains.jsonSchema.remote.http.setRemoteSchemaAccessAllowed
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class JsonFileResolverTest {
  private companion object {
    val projectFixture = projectFixture()
    const val URL = "https://example.com/schema.json"
  }

  private lateinit var scope: CoroutineScope

  @BeforeEach
  fun setUp() {
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }

  @AfterEach
  fun tearDown() {
    scope.cancel()
  }

  @Test
  fun `resolveSchemaByReference returns null immediately on a cold miss`(@TestDisposable disposable: Disposable) {
    val gate = CompletableDeferred<Unit>()
    val transport = GatedTransport(gate)
    val cache = RecordingCache()
    installService(cache, transport, disposable)

    val started = System.nanoTime()
    val result = JsonFileResolver.resolveSchemaByReference(null, URL, projectFixture.get())
    val elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started)

    assertEquals(null, result)
    assertTrue(elapsedMs < 1_000, "Cold resolution must not wait for the remote download")
    assertTrue(transport.started.await(5, TimeUnit.SECONDS), "A cold miss must still prefetch")
    gate.complete(Unit)
    assertTrue(cache.written.await(5, TimeUnit.SECONDS))
    assertEquals(1, transport.calls.get())
  }

  @Test
  fun `relative reference resolves when the origin directory name contains a space`() {
    val files = WriteAction.compute<Pair<VirtualFile, VirtualFile>, RuntimeException> {
      val root = TempFileSystem.getInstance().findFileByPath("/")
                 ?: error("temp root is missing")
      val existing = root.findChild("json resolver temp")
      existing?.delete(this)
      val dir = root.createChildDirectory(this, "json resolver temp")
      dir.createChildData(this, "referentSchema.json") to dir.createChildData(this, "baseSchema.json")
    }

    val resolvedUrl = JsonFileResolver.resolveSchemaUrlByReference(files.first, "baseSchema.json#/definitions/x")

    assertEquals("${files.second.url}#/definitions/x", resolvedUrl,
                 "a relative \$ref must resolve even when a path segment contains a space")
  }

  @Test
  fun `relative reference against temp vfs file resolves sibling`() {
    val files = WriteAction.compute<Pair<VirtualFile, VirtualFile>, RuntimeException> {
      val root = TempFileSystem.getInstance().findFileByPath("/")
                 ?: error("temp root is missing")
      val existing = root.findChild("json-resolver-temp")
      existing?.delete(this)
      val dir = root.createChildDirectory(this, "json-resolver-temp")
      dir.createChildData(this, "referentSchema.json") to dir.createChildData(this, "baseSchema.json")
    }

    val resolvedUrl = JsonFileResolver.resolveSchemaUrlByReference(files.first, "baseSchema.json#/definitions/x")
    assertEquals("${files.second.url}#/definitions/x", resolvedUrl)
    assertTrue(resolvedUrl!!.startsWith(JsonFileResolver.TEMP_URL))
    assertEquals(files.second, JsonFileResolver.resolveSchemaByReference(files.first, "baseSchema.json"))
    assertEquals(files.second, JsonFileResolver.urlToFile(files.second.url.replace("temp:///", "temp:/")))
  }

  private fun installService(cache: SchemaContentCache, transport: SchemaHttpTransport, disposable: Disposable) {
    setRemoteSchemaAccessAllowed(true, disposable)
    replaceRemoteSchemaServices(projectFixture.get(), scope, disposable, cache, transport)
  }

  private class RecordingCache : SchemaContentCache {
    val written = CountDownLatch(1)
    private var cached: CachedSchema? = null

    override fun read(url: SchemaUrl): CachedSchema? = cached

    override fun write(url: SchemaUrl, result: DownloadResult) {
      if (result is DownloadResult.Success) cached = CachedSchema.Content(result.content)
      written.countDown()
    }
  }

  private class GatedTransport(private val gate: CompletableDeferred<Unit>) : SchemaHttpTransport {
    val calls = AtomicInteger()
    val started = CountDownLatch(1)

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      started.countDown()
      gate.await()
      return DownloadResult.Success(SchemaContent(url.value.toByteArray()), null, null, url.value)
    }
  }
}
