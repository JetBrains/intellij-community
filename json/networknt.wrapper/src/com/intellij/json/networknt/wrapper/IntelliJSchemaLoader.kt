// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileManager
import com.jetbrains.jsonSchema.ide.JsonSchemaService
import com.networknt.schema.AbsoluteIri
import com.networknt.schema.resource.InputStreamSource
import com.networknt.schema.resource.IriResourceLoader
import com.networknt.schema.resource.ResourceLoader
import com.networknt.schema.resource.SchemaLoader
import java.io.ByteArrayInputStream

private val LOG = Logger.getInstance("com.intellij.json.networknt.wrapper.IntelliJSchemaLoader")

private val IRI_LOADER_PROTOCOLS = setOf("http", "https", "file")

/**
 * Bridges IntelliJ's VirtualFile-based schema resolution to networknt's SchemaLoader.
 *
 * Resource loader chain (tried in order):
 * 1. [IntelliJResourceLoader] — resolves via [JsonSchemaService] (catalog, user mappings, bundled schemas)
 * 2. [IriResourceLoader] — fallback for file://, http://, https:// URLs not in IntelliJ's catalog
 *    (e.g., schemas with $ref to local sibling files or remote definitions)
 */
class IntelliJSchemaLoader(project: Project) : SchemaLoader(
  emptyList(),
  listOf(IntelliJResourceLoader(project), IriResourceLoader.getInstance()),
)

/**
 * ResourceLoader implementation that uses IntelliJ's JsonSchemaService.
 *
 * Reads content eagerly because some VFS implementations (e.g., [HttpVirtualFileImpl][com.intellij.openapi.vfs.impl.http.HttpVirtualFileImpl])
 * throw [UnsupportedOperationException] from `getInputStream()`. Eager reading detects this
 * in `getResource()` so we can return `null` and let [IriResourceLoader] handle the URL.
 *
 * Non-standard protocols (e.g., `temp://`, `mock://`) are resolved via [VirtualFileManager]
 * and are never forwarded to [IriResourceLoader], which would crash with [java.net.MalformedURLException].
 */
private class IntelliJResourceLoader(private val project: Project) : ResourceLoader {
  override fun getResource(location: AbsoluteIri): InputStreamSource? {
    LOG.debug("IntelliJResourceLoader: resolving '$location'")

    val url = location.toString()

    // Always try IDE schema service first — resolves catalog, user mappings, bundled schemas,
    // and in-memory test schemas (LightVirtualFile with $id like "https://example.com/...")
    val schemaService = JsonSchemaService.Impl.get(project)
    val file = schemaService.findSchemaFileByReference(url, null)
    if (file != null) {
      LOG.debug("IntelliJResourceLoader: '$location' resolved to ${file.javaClass.simpleName}: ${file.url}")
      return readVirtualFile(file, location)
    }

    val protocol = url.substringBefore("://", missingDelimiterValue = "")
    if (protocol !in IRI_LOADER_PROTOCOLS) {
      // Non-standard protocols (temp://, mock://, etc.) cannot be handled by IriResourceLoader
      // because java.net.URL doesn't know them and throws MalformedURLException.
      // Try resolving via VirtualFileManager as a last resort.
      val vfsFile = VirtualFileManager.getInstance().findFileByUrl(url)
      if (vfsFile != null) {
        LOG.debug("IntelliJResourceLoader: '$location' resolved via VirtualFileManager to ${vfsFile.javaClass.simpleName}: ${vfsFile.url}")
        return readVirtualFile(vfsFile, location)
      }
      LOG.debug("IntelliJResourceLoader: '$location' with non-standard protocol '$protocol' not found in VFS, skipping IriResourceLoader")
      return null
    }

    LOG.debug("IntelliJResourceLoader: '$location' not found in JsonSchemaService, falling back to IriResourceLoader (protocol='$protocol')")
    return null
  }

  private fun readVirtualFile(file: VirtualFile, location: AbsoluteIri): InputStreamSource? {
    // Read eagerly: HttpVirtualFileImpl.getInputStream() throws UnsupportedOperationException,
    // LightVirtualFile works fine, LocalVirtualFile works fine.
    val bytes = try {
      file.contentsToByteArray()
    }
    catch (e: UnsupportedOperationException) {
      // HttpVirtualFileImpl doesn't support getInputStream()/contentsToByteArray().
      // Falling back to IriResourceLoader which handles HTTP URLs via java.net.URL.
      LOG.warn("VFS file ${file.javaClass.simpleName} doesn't support content reading for '$location', falling back to IriResourceLoader", e)
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
