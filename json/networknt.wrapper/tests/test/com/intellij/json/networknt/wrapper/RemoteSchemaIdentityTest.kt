// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.runReadActionBlocking
import com.intellij.openapi.components.service
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.testFramework.LightProjectDescriptor
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import com.jetbrains.jsonSchema.remote.http.CachedFile
import com.jetbrains.jsonSchema.remote.http.CachedSchema
import com.jetbrains.jsonSchema.remote.http.CachedStatus
import com.jetbrains.jsonSchema.remote.http.DownloadResult
import com.jetbrains.jsonSchema.remote.http.JsonSchemaFileCache
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService
import com.jetbrains.jsonSchema.remote.http.SchemaContent
import com.jetbrains.jsonSchema.remote.http.SchemaContentCache
import com.jetbrains.jsonSchema.remote.http.SchemaUrl
import com.networknt.schema.SpecificationVersion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.nio.file.Files
import java.util.concurrent.ConcurrentHashMap

class RemoteSchemaIdentityTest : BasePlatformTestCase() {
  private object IsolatedProjectDescriptor : LightProjectDescriptor()

  override fun getProjectDescriptor(): LightProjectDescriptor = IsolatedProjectDescriptor

  private lateinit var scope: CoroutineScope
  private lateinit var cache: JsonSchemaFileCache
  private lateinit var recordingCache: UrlRecordingCache

  override fun runInDispatchThread(): Boolean = false

  override fun setUp() {
    super.setUp()
    scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    clearCache()
    TestModeFlags.set(JsonFileResolver.REMOTE_ENABLED_IN_TESTS, true, testRootDisposable)
    val application = ApplicationManager.getApplication()
    cache = application.service<SchemaContentCache>() as JsonSchemaFileCache
    cache.clearMemoryForTest()
    recordingCache = UrlRecordingCache(cache)
    application.replaceService(SchemaContentCache::class.java, recordingCache, testRootDisposable)
    project.replaceService(JsonSchemaRemoteContentService::class.java, JsonSchemaRemoteContentService(project, scope), testRootDisposable)
  }

  override fun tearDown() {
    try {
      scope.cancel()
      clearCache()
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  fun `test cached root retrieved as YAML is compiled as YAML`() {
    val requested = SchemaUrl.parse("https://example.com/request/root")
    cache.write(requested, DownloadResult.Success(SchemaContent("type: string".toByteArray()), null, null,
                                                  "https://example.com/schemas/root.yaml"))

    val rootFile = cachedFile(requested)

    assertEquals("yaml", rootFile.extension)
    assertNotNull(NetworkntSchemaService.getInstance(project).getNetworkntSchema(rootFile, VERSION))
  }

  fun `test relative child ref resolves against the retrieval url`() {
    val requested = SchemaUrl.parse("https://example.com/request/root.json")
    val childUrl = SchemaUrl.parse("https://example.com/schemas/child.json")
    val wrongChildUrl = SchemaUrl.parse("https://example.com/request/child.json")
    cache.write(requested,
                DownloadResult.Success("""{"${'$'}ref": "child.json"}""".toByteArray().let(::SchemaContent),
                                       null, null, "https://example.com/schemas/root.json"))
    cache.write(childUrl, DownloadResult.Success(SchemaContent("""{"type": "string"}""".toByteArray()), null, null, childUrl.value))

    val rootFile = cachedFile(requested)
    val schema = NetworkntSchemaService.getInstance(project).getNetworkntSchema(rootFile, VERSION)

    val initializationFailure = runCatching { runReadActionBlocking { schema.initializeValidators() } }.exceptionOrNull()

    assertTrue("the child must be requested relative to the retrieval URL", recordingCache.requestedUrls.contains(childUrl))
    assertFalse("the child must not be requested relative to the request URL", recordingCache.requestedUrls.contains(wrongChildUrl))
    assertNull("the referenced child must be available", initializationFailure)
  }

  private class UrlRecordingCache(private val delegate: SchemaContentCache) : SchemaContentCache {
    val requestedUrls: MutableSet<SchemaUrl> = ConcurrentHashMap.newKeySet()

    override fun read(url: SchemaUrl): CachedSchema? {
      requestedUrls.add(url)
      return delegate.read(url)
    }

    override fun readStatus(url: SchemaUrl): CachedStatus? {
      requestedUrls.add(url)
      return delegate.readStatus(url)
    }

    override fun lookupFile(url: SchemaUrl): CachedFile? {
      requestedUrls.add(url)
      return delegate.lookupFile(url)
    }

    override fun load(url: SchemaUrl): Boolean = delegate.load(url)

    override fun write(url: SchemaUrl, result: DownloadResult) {
      delegate.write(url, result)
    }

    override fun refreshTimestamp(url: SchemaUrl) {
      delegate.refreshTimestamp(url)
    }
  }

  private fun clearCache() {
    val root = JsonSchemaFileCache.rootForTest()
    FileUtil.delete(root)
    Files.createDirectories(root)
    VirtualFileManager.getInstance().refreshAndFindFileByNioPath(root)?.refresh(false, true)
    if (::cache.isInitialized) cache.clearMemoryForTest()
  }

  /** Returns the file that [JsonSchemaFileCache.write] adopted for [url]. */
  private fun cachedFile(url: SchemaUrl): VirtualFile {
    val file = (cache.lookupFile(url) as? CachedFile.Available)?.file
    assertNotNull("expected an adopted file for ${url.value}", file)
    return file!!
  }

  private companion object {
    val VERSION = SpecificationVersion.DRAFT_7
  }
}
