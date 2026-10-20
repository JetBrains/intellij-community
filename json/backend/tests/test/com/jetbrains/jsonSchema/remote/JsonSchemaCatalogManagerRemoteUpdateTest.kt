// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote

import com.intellij.openapi.Disposable
import com.intellij.testFramework.junit5.TestApplication
import com.intellij.testFramework.junit5.TestDisposable
import com.intellij.testFramework.junit5.fixture.projectFixture
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.remote.http.CachedSchema
import com.jetbrains.jsonSchema.remote.http.DownloadResult
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService
import com.jetbrains.jsonSchema.remote.http.SchemaContent
import com.jetbrains.jsonSchema.remote.http.SchemaContentCache
import com.jetbrains.jsonSchema.remote.http.SchemaHttpTransport
import com.jetbrains.jsonSchema.remote.http.SchemaUrl
import com.jetbrains.jsonSchema.remote.http.replaceRemoteSchemaServices
import com.jetbrains.jsonSchema.remote.http.setRemoteSchemaAccessAllowed
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

@TestApplication
class JsonSchemaCatalogManagerRemoteUpdateTest {
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
  fun testRegisterCatalogUpdateCallbackFiresOnlyForCatalogUrl() {
    installService(RecordingCache(), RecordingTransport())
    val catalogManager = JsonSchemaService.Impl.get(project).catalogManager

    val notified = CountDownLatch(1)
    val callback = Runnable { notified.countDown() }
    catalogManager.registerCatalogUpdateCallback(callback)

    val service = JsonSchemaRemoteContentService.getInstance(project)
    assertTrue(service.prefetch(SchemaUrl.parse(OTHER_URL)))
    assertFalse(notified.await(500, TimeUnit.MILLISECONDS), "callback must not fire for an unrelated schema url")

    assertTrue(service.prefetch(SchemaUrl.parse(CATALOG_URL)))
    assertTrue(notified.await(5, TimeUnit.SECONDS), "callback must fire once the catalog url downloads")

    catalogManager.unregisterCatalogUpdateCallback(callback)
  }

  @Test
  fun testUnregisterCatalogUpdateCallbackStopsNotifications() {
    val cache = RecordingCache()
    installService(cache, RecordingTransport())
    val catalogManager = JsonSchemaService.Impl.get(project).catalogManager

    val notifications = AtomicInteger()
    val callback = Runnable { notifications.incrementAndGet() }
    catalogManager.registerCatalogUpdateCallback(callback)
    catalogManager.unregisterCatalogUpdateCallback(callback)

    val service = JsonSchemaRemoteContentService.getInstance(project)
    assertTrue(service.prefetch(SchemaUrl.parse(CATALOG_URL)))
    assertTrue(cache.written.await(5, TimeUnit.SECONDS))
    assertEquals(0, notifications.get())
  }

  @Test
  fun testTriggerUpdateCatalogPrefetchesCatalogUrl() {
    val transport = RecordingTransport()
    installService(RecordingCache(), transport)
    val catalogManager = JsonSchemaService.Impl.get(project).catalogManager

    catalogManager.triggerUpdateCatalog(project)

    assertTrue(transport.started.await(5, TimeUnit.SECONDS))
    assertEquals(CATALOG_URL, transport.lastUrl.get())
  }

  private fun installService(cache: SchemaContentCache, transport: SchemaHttpTransport) {
    setRemoteSchemaAccessAllowed(true, disposable)
    replaceRemoteSchemaServices(project, scope, disposable, cache, transport)
  }

  private class RecordingCache : SchemaContentCache {
    val written: CountDownLatch = CountDownLatch(1)
    private val cached = ConcurrentHashMap<SchemaUrl, CachedSchema>()

    override fun read(url: SchemaUrl): CachedSchema? = cached[url]

    override fun write(url: SchemaUrl, result: DownloadResult) {
      if (result is DownloadResult.Success) cached[url] = CachedSchema.Content(result.content)
      written.countDown()
    }
  }

  private class RecordingTransport : SchemaHttpTransport {
    val started: CountDownLatch = CountDownLatch(1)
    val lastUrl = AtomicReference<String>()

    override suspend fun download(url: SchemaUrl): DownloadResult {
      lastUrl.set(url.value)
      started.countDown()
      return DownloadResult.Success(SchemaContent(url.value.toByteArray()), null, null, url.value)
    }
  }

  private companion object {
    private val projectFixture = projectFixture(openAfterCreation = true)

    const val OTHER_URL = "https://example.com/other-schema.json"

    // Mirrors the package-private JsonSchemaCatalogManager.DEFAULT_CATALOG_HTTPS, not visible from this test module.
    const val CATALOG_URL = "https://schemastore.org/api/json/catalog.json"
  }
}
