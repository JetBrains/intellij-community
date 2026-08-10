// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.serviceIfCreated
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.testFramework.TestModeFlags
import com.intellij.testFramework.replaceService
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import kotlinx.coroutines.CoroutineScope
import org.jetbrains.annotations.TestOnly
import java.nio.file.Files
import java.nio.file.Path
import kotlin.io.path.extension
import kotlin.io.path.readText
import kotlin.io.path.writeText
import kotlin.time.Duration

private val TIMESTAMP_REGEX = Regex("\"timestamp\":(\\d+)")

@TestOnly
fun setRemoteSchemaAccessAllowed(allowed: Boolean, disposable: Disposable) {
  TestModeFlags.set(JsonFileResolver.REMOTE_ENABLED_IN_TESTS, allowed, disposable)
}

/**
 * Replaces the remote schema services until [disposable] is disposed.
 * A `null` [cache] or [transport] keeps the registered application service.
 * Always registers a new [JsonSchemaRemoteContentService], so no download state stays from an earlier test.
 */
fun replaceRemoteSchemaServices(
  project: Project,
  scope: CoroutineScope,
  disposable: Disposable,
  cache: SchemaContentCache? = null,
  transport: SchemaHttpTransport? = null,
): JsonSchemaRemoteContentService {
  val application = ApplicationManager.getApplication()
  cache?.let { application.replaceService(SchemaContentCache::class.java, it, disposable) }
  transport?.let { application.replaceService(SchemaHttpTransport::class.java, it, disposable) }
  val service = JsonSchemaRemoteContentService(project, scope)
  project.replaceService(JsonSchemaRemoteContentService::class.java, service, disposable)
  return service
}

/**
 * Deletes all entries of the remote schema file cache and refreshes the VFS, so no stale file stays.
 * Also clears the memory of the registered cache.
 */
fun clearRemoteSchemaCache(): Path {
  val root = JsonSchemaFileCache.rootForTest()
  FileUtil.delete(root)
  Files.createDirectories(root)
  VfsUtil.markDirtyAndRefresh(false, true, true, root.toFile())
  clearRegisteredRemoteSchemaCacheMemory()
  return root
}

/**
 * Moves the timestamps of all entries of the remote schema file cache back by [age].
 * Also clears the memory of the registered cache, so that only a [SchemaContentCache.load] sees the new timestamps.
 */
fun ageRemoteSchemaCache(age: Duration) {
  Files.list(JsonSchemaFileCache.rootForTest()).use { files ->
    files.filter { it.extension == "meta" }.forEach { metadata ->
      metadata.writeText(TIMESTAMP_REGEX.replace(metadata.readText()) { match ->
        "\"timestamp\":${match.groupValues[1].toLong() - age.inWholeMilliseconds}"
      })
    }
  }
  clearRegisteredRemoteSchemaCacheMemory()
}

private fun clearRegisteredRemoteSchemaCacheMemory() {
  (ApplicationManager.getApplication().serviceIfCreated<SchemaContentCache>() as? JsonSchemaFileCache)?.clearMemoryForTest()
}
