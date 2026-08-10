// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.jetbrains.jsonSchema.remote.http.JsonSchemaRemoteContentService
import com.jetbrains.jsonSchema.remote.http.RemoteSchemaContentProvider
import com.jetbrains.jsonSchema.remote.http.SchemaUrl
import com.networknt.schema.AbsoluteIri
import com.networknt.schema.resource.InputStreamSource
import com.networknt.schema.resource.ResourceLoader
import com.networknt.schema.resource.SchemaLoader
import java.io.ByteArrayInputStream
import java.nio.file.AccessDeniedException

private val LOG = Logger.getInstance("com.intellij.json.networknt.wrapper.IntelliJSchemaLoader")

/**
 * Bridges IntelliJ's VirtualFile-based schema resolution to networknt's SchemaLoader.
 *
 * [IntelliJResourceLoader] resolves schema references through IntelliJ's VFS.
 * HTTP(S) references use the IDE cache and asynchronous prefetch service instead.
 */
class IntelliJSchemaLoader(project: Project, schemaFile: VirtualFile) : SchemaLoader(
  emptyList(),
  listOf(IntelliJResourceLoader(project, schemaFile, JsonSchemaRemoteContentService.getInstance(project))),
)

/**
 * ResourceLoader implementation that uses IntelliJ's JsonSchemaService.
 *
 * Reads content eagerly because some virtual file implementations throw [UnsupportedOperationException] from `getInputStream()`.
 * Remote reads must go through the bounded cache service.
 */
private class IntelliJResourceLoader(
  private val project: Project,
  private val schemaFile: VirtualFile,
  private val remoteContent: RemoteSchemaContentProvider,
) : ResourceLoader {
  override fun getResource(location: AbsoluteIri): InputStreamSource? {
    LOG.debug("IntelliJResourceLoader: resolving '$location'")

    val url = location.toString()
    val schemaService = JsonSchemaService.Impl.get(project)

    if (url.isHttpUrl()) {
      // HTTP $ids can also identify bundled, user-mapped, or in-memory test schemas.
      // Resolve those without entering remote content loading.
      if (schemaService.shouldRestrictSchemaReferences(schemaFile)) {
        // For a restricted referent, this accepts only the registered schemas that an untrusted project can read.
        schemaService.findSchemaFileByReference(url, schemaFile)?.let { file ->
          return readVirtualFile(file, location)
        }
        return forbiddenUntilTrusted(url)
      }
      schemaService.findBuiltInSchemaByReference(url)?.let { file ->
        return readVirtualFile(file, location)
      }
      return resolveRemote(url)
    }

    // Always try IDE schema service first — resolves catalog, user mappings, bundled schemas,
    // and in-memory test schemas (LightVirtualFile with $id like "https://example.com/...")
    val file = schemaService.findSchemaFileByReference(url, schemaFile)
    if (file != null) {
      LOG.debug("IntelliJResourceLoader: '$location' resolved to ${file.javaClass.simpleName}: ${file.url}")
      val source = readVirtualFile(file, location)
      if (source != null) return source
    }

    if (schemaService.shouldRestrictSchemaReferences(schemaFile)) {
      LOG.debug("IntelliJResourceLoader: '$location' is forbidden for untrusted project schema '${schemaFile.url}'")
      return InputStreamSource {
        throw AccessDeniedException(url, null, "Schema reference is forbidden until the project is trusted")
      }
    }

    val vfsFile = VirtualFileManager.getInstance().findFileByUrl(url)
    if (vfsFile != null) {
      LOG.debug("IntelliJResourceLoader: '$location' resolved via VirtualFileManager to ${vfsFile.javaClass.simpleName}: ${vfsFile.url}")
      return readVirtualFile(vfsFile, location)
    }
    LOG.debug("IntelliJResourceLoader: '$location' not found in VFS")
    return null
  }

  /** Returns `null` when the remote content is not available now. The validation then reports the reference as not found. */
  private fun resolveRemote(url: String): InputStreamSource? {
    if (!remoteContent.isAllowed()) return notLoaded(url, "remote schema loading is disabled")
    val schemaUrl = try {
      SchemaUrl.parse(url)
    }
    catch (e: Exception) {
      return notLoaded(url, "invalid remote schema URL: ${e.message}")
    }
    remoteContent.getCached(schemaUrl)?.let { content ->
      return InputStreamSource { ByteArrayInputStream(content.bytes) }
    }
    if (!remoteContent.prefetch(schemaUrl)) return notLoaded(url, "remote schema loading is disabled")
    return null
  }

  private fun notLoaded(url: String, reason: String): InputStreamSource? {
    LOG.debug("IntelliJResourceLoader: '$url' is not loaded: $reason")
    return null
  }

  private fun forbiddenUntilTrusted(url: String): InputStreamSource = InputStreamSource {
    throw AccessDeniedException(url, null, "Schema reference is forbidden until the project is trusted")
  }

  private fun readVirtualFile(file: VirtualFile, location: AbsoluteIri): InputStreamSource? {
    // Some virtual file implementations do not support content reading.
    val bytes = try {
      file.contentsToByteArray()
    }
    catch (e: UnsupportedOperationException) {
      // Local file references may still use the compatibility IRI loader after this read fails.
      LOG.warn("VFS file ${file.javaClass.simpleName} doesn't support content reading for '$location'", e)
      return null
    }
    catch (e: Exception) {
      LOG.error("Failed to read schema content from VFS for '$location' (${file.javaClass.simpleName})", e)
      return null
    }

    LOG.debug("IntelliJResourceLoader: loaded ${bytes.size} bytes from '${file.url}'")
    return InputStreamSource { ByteArrayInputStream(bytes) }
  }
}

private fun String.isHttpUrl(): Boolean = startsWith("http://") || startsWith("https://")
