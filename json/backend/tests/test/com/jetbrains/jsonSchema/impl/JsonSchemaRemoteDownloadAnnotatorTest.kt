// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.impl

import com.intellij.lang.Language
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.Disposable
import com.intellij.platform.testFramework.junit5.codeInsight.fixture.codeInsightModuleFixture
import com.intellij.testFramework.LightVirtualFile
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.intellij.util.WaitFor
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
class JsonSchemaRemoteDownloadAnnotatorTest {
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
  fun testUnavailableUrlIsWarningOnSchemaProperty() {
    val url = "https://example.test/annotator-404.json"
    installService(StatusCache(CachedSchema.Unavailable(404, "Not Found")))
    myFixture.configureByText("a.json", """{"${'$'}schema": "$url"}""")
    val infos = myFixture.doHighlighting()
    assertTrue(
      infos.any { it.severity == HighlightSeverity.WARNING && it.description.contains("Cannot download JSON schema") },
    )
    assertTrue(infos.none { it.severity == HighlightSeverity.ERROR })
  }

  @Test
  fun testUnavailableUrlIsOneWarningInJson5() {
    val url = "https://example.test/annotator-json5-404.json"
    installService(StatusCache(CachedSchema.Unavailable(404, "Not Found")))
    myFixture.configureByText("a.json5", """{"${'$'}schema": "$url"}""")
    val infos = myFixture.doHighlighting()
    assertEquals(1, infos.count { it.severity == HighlightSeverity.WARNING && it.description.contains("Cannot download JSON schema") })
  }

  @Test
  fun testFailedIsWeakWarning() {
    val url = "https://example.test/annotator-failed.json"
    val service = installService(StatusCache(), FailingTransport())
    assertNull(service.downloadAndWaitForTest(SchemaUrl.parse(url)))
    myFixture.configureByText("a.json", """{"${'$'}schema": "$url"}""")
    val infos = myFixture.doHighlighting()
    assertTrue(infos.any { it.severity == HighlightSeverity.WEAK_WARNING && it.description.contains("Cannot download JSON schema") })
    assertTrue(infos.none { it.severity == HighlightSeverity.ERROR })
    assertTrue(infos.none { it.severity == HighlightSeverity.WARNING && it.description.contains("Cannot download JSON schema") })
  }

  @Test
  fun testFailedOffersRetryThatDownloadsAgain() {
    val url = "https://example.test/annotator-retry.json"
    val transport = FailingTransport()
    val service = installService(StatusCache(), transport)
    assertNull(service.downloadAndWaitForTest(SchemaUrl.parse(url)))
    myFixture.configureByText("a.json", """{"${'$'}schema": "<caret>$url"}""")

    myFixture.launchAction(myFixture.findSingleIntention("Retry downloading JSON schema"))

    assertTrue(waitFor(5_000) { transport.calls.get() == 2 && !service.hasInFlight(url) })
  }

  @Test
  fun testUnavailableOffersRetryThatDownloadsAgain() {
    val url = "https://example.test/annotator-unavailable-retry.json"
    val transport = CountingTransport()
    val service = installService(StatusCache(CachedSchema.Unavailable(403, "Forbidden")), transport)
    myFixture.configureByText("a.json", """{"${'$'}schema": "<caret>$url"}""")

    myFixture.launchAction(myFixture.findSingleIntention("Retry downloading JSON schema"))

    assertTrue(waitFor(5_000) { transport.calls.get() == 1 && !service.hasInFlight(url) })
    assertFalse(service.isUnavailable(url))
  }

  @Test
  fun testRejectedOffersNoRetry() {
    val url = "https://example.test/annotator-rejected-no-retry.json"
    val service = installService(StatusCache(), RejectedTransport())
    assertNull(service.downloadAndWaitForTest(SchemaUrl.parse(url)))
    myFixture.configureByText("a.json", """{"${'$'}schema": "<caret>$url"}""")

    assertTrue(myFixture.filterAvailableIntentions("Retry downloading JSON schema").isEmpty())
  }

  @Test
  fun testRejectedIsWarning() {
    val url = "https://example.test/annotator-rejected.json"
    val service = installService(StatusCache(), RejectedTransport())
    assertNull(service.downloadAndWaitForTest(SchemaUrl.parse(url)))
    myFixture.configureByText("a.json", """{"${'$'}schema": "$url"}""")
    val infos = myFixture.doHighlighting()
    assertTrue(
      infos.any { it.severity == HighlightSeverity.WARNING && it.description.contains("Cannot download JSON schema") },
    )
    assertTrue(infos.none { it.severity == HighlightSeverity.ERROR })
  }

  @Test
  fun testSuccessIsQuiet() {
    val url = "https://example.test/annotator-success.json"
    installService(StatusCache(CachedSchema.Content(SchemaContent("{}".toByteArray()))))
    myFixture.configureByText("a.json", """{"${'$'}schema": "$url"}""")
    val infos = myFixture.doHighlighting()
    assertTrue(infos.none { it.description?.contains("Cannot download JSON schema") == true })
  }

  @Test
  fun testInFlightIsQuiet() {
    val url = "https://example.test/annotator-inflight.json"
    val gate = CompletableDeferred<Unit>()
    try {
      installService(StatusCache(), GatedTransport(gate))
      val parsed = JsonSchemaRemoteContentService.parseOrNull(url)!!
      JsonSchemaRemoteContentService.getInstance(project).prefetch(parsed)
      myFixture.configureByText("a.json", """{"${'$'}schema": "$url"}""")
      val infos = myFixture.doHighlighting()
      assertTrue(infos.none { it.description?.contains("Cannot download JSON schema") == true })
    }
    finally {
      gate.complete(Unit)
    }
  }

  @Test
  fun testColdIsQuiet() {
    val url = "https://example.test/annotator-cold.json"
    installService(StatusCache())
    myFixture.configureByText("a.json", """{"${'$'}schema": "$url"}""")
    val infos = myFixture.doHighlighting()
    assertTrue(infos.none { it.description?.contains("Cannot download JSON schema") == true })
  }

  @Test
  fun testUnavailableUrlIsWarningOnSchemaComment() {
    val yaml = Language.findLanguageByID("yaml") ?: return
    val fileType = yaml.associatedFileType ?: return
    val url = "https://example.test/annotator-comment-404.json"
    installService(StatusCache(CachedSchema.Unavailable(404, "Not Found")))
    myFixture.configureByText(fileType, "# ${'$'}schema: $url\nkey: 1")
    val infos = myFixture.doHighlighting()
    assertTrue(
      infos.any { it.severity == HighlightSeverity.WARNING && it.description.contains("Cannot download JSON schema") },
    )
    assertTrue(infos.none { it.severity == HighlightSeverity.ERROR })
  }

  private fun waitFor(timeoutMs: Int, predicate: () -> Boolean): Boolean =
    object : WaitFor(timeoutMs) {
      override fun condition(): Boolean = predicate()
    }.isConditionRealized

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

  private class CountingTransport : SchemaHttpTransport {
    val calls = AtomicInteger()

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      return DownloadResult.Success(SchemaContent("{}".toByteArray()), null, null, url.value)
    }
  }

  private class FailingTransport : SchemaHttpTransport {
    val calls = AtomicInteger()

    override suspend fun download(url: SchemaUrl): DownloadResult {
      calls.incrementAndGet()
      return DownloadResult.Failed(IOException("boom"))
    }
  }

  private class RejectedTransport : SchemaHttpTransport {
    override suspend fun download(url: SchemaUrl): DownloadResult =
      DownloadResult.Rejected("unsupported")
  }
}
