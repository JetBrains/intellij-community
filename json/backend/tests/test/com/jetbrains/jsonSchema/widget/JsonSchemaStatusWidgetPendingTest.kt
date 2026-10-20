// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.widget

import com.intellij.json.JsonBundle
import com.intellij.openapi.Disposable
import com.intellij.openapi.application.readAction
import com.intellij.openapi.application.readActionBlocking
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.common.timeoutRunBlocking
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.extension.JsonSchemaInfo
import com.jetbrains.jsonSchema.impl.JsonCachedValues
import com.jetbrains.jsonSchema.impl.JsonSchemaByCommentProvider
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import com.jetbrains.jsonSchema.remote.http.CachedFile
import com.jetbrains.jsonSchema.remote.http.CachedSchema
import com.jetbrains.jsonSchema.remote.http.DownloadResult
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService
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
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.io.IOException
import java.util.concurrent.atomic.AtomicInteger

@TestApplication
class JsonSchemaStatusWidgetPendingTest {
  companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)
  }

  private val myFixture by codeInsightModuleFixture(projectFixture)
  private val project get() = projectFixture.get()

  @TestDisposable
  lateinit var disposable: Disposable

  private lateinit var scope: CoroutineScope

  @BeforeEach
  fun setUp() {
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
  }

  @AfterEach
  fun tearDown() {
    if (::scope.isInitialized) scope.cancel()
  }

  @Test
  fun testPendingRemoteSchemaUrlIsDerivedFromSchemaProperty(): Unit = timeoutRunBlocking {
    val schemaUrl = "https://example.test/unique-pending-schema.json"
    val psiFile = myFixture.configureByText("pending-widget-unique.json", """{"${'$'}schema": "$schemaUrl"}""")

    val url = readAction { JsonCachedValues.getSchemaUrlFromSchemaProperty(psiFile.virtualFile, project) }
    assertEquals(schemaUrl, url)
    assertTrue(JsonFileResolver.isHttpPath(url!!))
    val schemaName = JsonSchemaInfo(url).description
    assertEquals("unique-pending-schema.json", schemaName)
    assertFalse(schemaName.contains(JsonBundle.message("schema.widget.no.schema.label"), ignoreCase = true))
  }

  @Test
  fun testFileWithoutRemoteSchemaHasNoPendingUrl(): Unit = timeoutRunBlocking {
    val psiFile = myFixture.configureByText("no-schema-widget-unique.json", """{"foo": 1}""")

    assertNull(readAction { JsonCachedValues.getSchemaUrlFromSchemaProperty(psiFile.virtualFile, project) })
    assertNull(readAction { JsonSchemaByCommentProvider.getCommentSchema(psiFile.virtualFile, project) })
  }

  @Test
  fun testUnavailableStatusQuery() {
    val service = JsonSchemaRemoteContentService.getInstance(project)
    assertFalse(service.isUnavailable("https://example.test/not-in-cache.json"))
  }

  @Test
  fun testPeekHitDoesNotRecurseIntoDoGetWidgetState(): Unit = timeoutRunBlocking {
    val url = "https://example.test/widget-peek.json"
    val cache = StatusCache(CachedSchema.Content(SchemaContent("{}".toByteArray())))
    installService(cache)
    val psi = myFixture.configureByText("w.json", """{"${'$'}schema": "$url"}""")
    val state = readActionBlocking { JsonSchemaStatusWidget.pendingStateForTests(project, psi.virtualFile, true) }
    assertFalse(state.text.contains(JsonBundle.message("schema.widget.download.failed.label")))
    assertFalse(state.text.contains(JsonBundle.message("schema.widget.download.in.progress.label")))
    assertFalse(state.text.contains(JsonBundle.message("schema.widget.no.schema.label")))
  }

  @Test
  fun testInFlightShowsDownloading(): Unit = timeoutRunBlocking {
    val url = "https://example.test/widget-inflight.json"
    val gate = CompletableDeferred<Unit>()
    installService(StatusCache(), GatedTransport(gate))
    val parsed = JsonSchemaRemoteContentService.parseOrNull(url)!!
    JsonSchemaRemoteContentService.getInstance(project).prefetch(parsed)
    val psi = myFixture.configureByText("w.json", """{"${'$'}schema": "$url"}""")
    val state = readActionBlocking { JsonSchemaStatusWidget.pendingStateForTests(project, psi.virtualFile, true) }
    assertEquals(JsonBundle.message("schema.widget.download.in.progress.label"), state.text)
    gate.complete(Unit)
  }

  @Test
  fun testFailedShowsFailedAndDoesNotPrefetchAgain(): Unit = timeoutRunBlocking {
    val url = "https://example.test/widget-failed.json"
    val transport = CountingFailingTransport()
    val service = installService(StatusCache(), transport)
    assertNull(service.downloadAndWaitForTest(JsonSchemaRemoteContentService.parseOrNull(url)!!))
    assertEquals(1, transport.calls.get())
    val psi = myFixture.configureByText("w.json", """{"${'$'}schema": "$url"}""")
    val state = readActionBlocking { JsonSchemaStatusWidget.widgetStateForTests(project, psi.virtualFile, true) }
    assertEquals(JsonBundle.message("schema.widget.download.failed.label"), state.text)
    assertEquals(1, transport.calls.get())
  }

  @Test
  fun testUnavailableShowsFailedNotNoSchema(): Unit = timeoutRunBlocking {
    val url = "https://example.test/widget-404.json"
    installService(StatusCache(CachedSchema.Unavailable(404, "Not Found")))
    val psi = myFixture.configureByText("w.json", """{"${'$'}schema": "$url"}""")
    val state = readActionBlocking { JsonSchemaStatusWidget.pendingStateForTests(project, psi.virtualFile, true) }
    assertEquals(JsonBundle.message("schema.widget.download.failed.label"), state.text)
    assertFalse(state.text.contains(JsonBundle.message("schema.widget.no.schema.label")))
  }

  @Test
  fun testColdPrefetchesAndShowsDownloading(): Unit = timeoutRunBlocking {
    val url = "https://example.test/widget-cold.json"
    val gate = CompletableDeferred<Unit>()
    installService(StatusCache(), GatedTransport(gate))
    val psi = myFixture.configureByText("w.json", """{"${'$'}schema": "$url"}""")
    val state = readActionBlocking { JsonSchemaStatusWidget.pendingStateForTests(project, psi.virtualFile, true) }
    assertEquals(JsonBundle.message("schema.widget.download.in.progress.label"), state.text)
    assertTrue(JsonSchemaRemoteContentService.getInstance(project).hasInFlight(url))
    gate.complete(Unit)
  }

  private fun installService(
    cache: SchemaContentCache,
    transport: SchemaHttpTransport = GatedTransport(CompletableDeferred()),
  ): JsonSchemaRemoteContentService {
    setRemoteSchemaAccessAllowed(true, disposable)
    return replaceRemoteSchemaServices(project, scope, disposable, cache, transport)
  }

  private class StatusCache(private var cached: CachedSchema? = null) : SchemaContentCache {
    override fun read(url: SchemaUrl): CachedSchema? = cached

    override fun lookupFile(url: SchemaUrl): CachedFile? = when (val value = cached) {
      is CachedSchema.Content -> CachedFile.Available(LightVirtualFile("schema.json", String(value.content.bytes)), value.isStale)
      is CachedSchema.Unavailable -> CachedFile.Unavailable(value.code, value.message)
      null -> null
    }

    override fun write(url: SchemaUrl, result: DownloadResult) {
      when (result) {
        is DownloadResult.Success -> cached = CachedSchema.Content(result.content)
        is DownloadResult.Error -> cached = CachedSchema.Unavailable(result.code, result.message)
        else -> Unit
      }
    }
  }

  private class GatedTransport(private val gate: CompletableDeferred<Unit>) : SchemaHttpTransport {
    override suspend fun download(url: SchemaUrl): DownloadResult {
      gate.await()
      return DownloadResult.Success(SchemaContent(url.value.toByteArray()), null, null, url.value)
    }
  }

  private class CountingFailingTransport : SchemaHttpTransport {
    val calls = AtomicInteger()

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      return DownloadResult.Failed(IOException("boom"))
    }
  }
}
