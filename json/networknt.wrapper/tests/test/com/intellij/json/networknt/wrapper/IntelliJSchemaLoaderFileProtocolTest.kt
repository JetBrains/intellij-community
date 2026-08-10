// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFileManager
import com.intellij.openapi.vfs.newvfs.impl.VfsRootAccess
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.replaceService
import com.jetbrains.jsonSchema.remote.http.CachedSchema
import com.jetbrains.jsonSchema.remote.http.DownloadResult
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService
import com.jetbrains.jsonSchema.remote.http.SchemaContentCache
import com.jetbrains.jsonSchema.remote.http.SchemaHttpTransport
import com.jetbrains.jsonSchema.remote.http.SchemaUrl
import com.networknt.schema.AbsoluteIri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import java.nio.file.Files
import java.nio.file.Path

private const val FROM_DISK = """{"title": "from-disk"}"""

class IntelliJSchemaLoaderFileProtocolTest : BasePlatformTestCase() {

  private object UnusedCache : SchemaContentCache {
    override fun read(url: SchemaUrl): CachedSchema? = error("remote content is denied")
    override fun write(url: SchemaUrl, result: DownloadResult): Unit = error("remote content is denied")
  }

  private object UnusedTransport : SchemaHttpTransport {
    override suspend fun download(url: SchemaUrl): DownloadResult = error("remote content is denied")
  }

  private lateinit var outside: Path
  private lateinit var missing: Path

  override fun setUp() {
    super.setUp()
    outside = Files.writeString(Files.createTempFile("outside-schema", ".json"), FROM_DISK)
    missing = outside.parent.resolve("never-created-schema-${System.nanoTime()}.json")
    VfsRootAccess.allowRootAccess(testRootDisposable, outside.parent.toString())
    val scope = CoroutineScope(SupervisorJob())
    Disposer.register(testRootDisposable) { scope.cancel() }
    val application = ApplicationManager.getApplication()
    application.replaceService(SchemaContentCache::class.java, UnusedCache, testRootDisposable)
    application.replaceService(SchemaHttpTransport::class.java, UnusedTransport, testRootDisposable)
    project.replaceService(JsonSchemaRemoteContentService::class.java, JsonSchemaRemoteContentService(project, scope), testRootDisposable)
  }

  override fun tearDown() {
    try {
      Files.deleteIfExists(outside)
    }
    catch (e: Throwable) {
      addSuppressedException(e)
    }
    finally {
      super.tearDown()
    }
  }

  private fun fileUrl(): String = "file://" + outside.toString().replace('\\', '/')

  private fun missingUrl(): String = "file://" + missing.toString().replace('\\', '/')

  fun `test file ref absent from the VFS is not read`() {
    val schemaFile = myFixture.addFileToProject("root.json", "{}").virtualFile
    val loader = IntelliJSchemaLoader(project, schemaFile)

    assertNull("a file:// reference the VFS cannot resolve must not be read through java.net.URL", loader.getSchemaResource(AbsoluteIri.of(missingUrl())))
  }

  fun `test file ref visible in the VFS resolves through the VFS`() {
    VirtualFileManager.getInstance().refreshAndFindFileByUrl(fileUrl())
    val schemaFile = myFixture.addFileToProject("root.json", "{}").virtualFile
    val loader = IntelliJSchemaLoader(project, schemaFile)

    val source = requireNotNull(loader.getSchemaResource(AbsoluteIri.of(fileUrl())))

    assertEquals(FROM_DISK, source.inputStream.reader().use { it.readText() })
  }
}
