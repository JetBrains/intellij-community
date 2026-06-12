// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.json.networknt.wrapper

import com.github.benmanes.caffeine.cache.AsyncLoadingCache
import com.github.benmanes.caffeine.cache.Caffeine
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.networknt.schema.InputFormat
import com.networknt.schema.Schema
import com.networknt.schema.SchemaLocation
import com.networknt.schema.SchemaRegistry
import com.networknt.schema.SchemaRegistryConfig
import com.networknt.schema.Specification
import com.networknt.schema.SpecificationVersion
import com.networknt.schema.dialect.DefaultDialectRegistry
import com.networknt.schema.dialect.Dialect
import com.networknt.schema.dialect.DialectRegistry
import com.networknt.schema.regex.JoniRegularExpressionFactory
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.future.future
import org.jetbrains.annotations.VisibleForTesting

/**
 * Project-level service that manages networknt SchemaRegistry instances and caches Schema objects.
 *
 * Schema instances are cached by (file URL, modification stamp) to avoid re-parsing unchanged schemas.
 * The cache is bounded (max 5 entries) and thread-safe.
 *
 * Uses `preloadSchema(false)` for lazy `$ref` resolution — critical for large schemas like Azure ARM
 * which have 2550+ `$ref` entries (eager DFS would take ~28s).
 */
@Service(Service.Level.PROJECT)
class NetworkntSchemaService(private val project: Project, private val scope: CoroutineScope) {

  companion object {
    private val LOG = Logger.getInstance(NetworkntSchemaService::class.java)
    private const val MAX_CACHE_SIZE = 5

    @JvmStatic
    fun getInstance(project: Project): NetworkntSchemaService {
      return project.getService(NetworkntSchemaService::class.java)
    }
  }

  /**
   * Identity-keyed by [file] so synthetic providers (e.g. [com.intellij.testFramework.LightVirtualFile]
   * referenced by `mock://` URLs that aren't registered in the global VFS) resolve via the original
   * VirtualFile reference instead of going through `VirtualFileManager.findFileByUrl`. The
   * modification stamp keeps the cache invariant against schema edits, and the spec version
   * separates dialect-specific compilations of the same source.
   */
  private data class SchemaCacheKey(val file: VirtualFile, val stamp: Long, val version: SpecificationVersion)

  private val schemaCache: AsyncLoadingCache<SchemaCacheKey, Schema> =
    Caffeine.newBuilder()
      .maximumSize(MAX_CACHE_SIZE.toLong())
      .buildAsync { key, _ ->
        scope.future(Dispatchers.Default) {
          val file = key.file
          val schemaText = String(file.contentsToByteArray(), file.charset)
          val registry = buildRegistry(key.version)
          registry.getSchema(SchemaLocation.of(file.url), schemaText, detectInputFormat(file)).also {
            LOG.debug("Schema compiled asynchronously: ${file.url}")
          }
        }
      }

  /**
   * Returns a compiled Schema for the given schema file.
   *
   * On cache miss, compilation starts asynchronously on [Dispatchers.Default] (outside read action).
   * The calling thread waits via [ProgressIndicatorUtils.awaitWithCheckCanceled]: if the current
   * progress indicator is cancelled (e.g., a write action arrives), [com.intellij.openapi.progress.ProcessCanceledException] is
   * thrown, but background compilation continues. The next daemon pass finds the schema ready.
   */
  fun getNetworkntSchema(schemaFile: VirtualFile, version: SpecificationVersion): Schema {
    val cacheKey = SchemaCacheKey(schemaFile, schemaFile.modificationStamp, version)

    val future = schemaCache.get(cacheKey)
    val cacheHit = future.isDone && !future.isCompletedExceptionally

    if (cacheHit) LOG.debug("Schema cache hit for ${schemaFile.url}")
    else LOG.debug("Schema cache MISS for ${schemaFile.name} (stamp=${schemaFile.modificationStamp})")

    val schema = ProgressIndicatorUtils.awaitWithCheckCanceled(future)

    return schema
  }

  fun invalidateAllCaches(reason: String = "unknown") {
    val map = schemaCache.asMap()
    val size = map.size
    if (size > 0) LOG.warn("Schema cache invalidateAll: reason=$reason, evicted=$size")
    map.clear()
  }

  /**
   * Picks the parser format based on the schema file extension.
   *
   * networknt's `SchemaRegistry.getSchema(..., InputFormat)` dispatches between Jackson's
   * JSON `ObjectMapper` and `YAMLMapper`. Bare YAML schemas (e.g. `type: array` without
   * quotes) fail JSON parsing with `Unrecognized token`, so we must route them through
   * the YAML parser. JSON-syntax inside `.yaml`/`.yml` files still works because YAML is
   * a JSON superset and YAMLMapper accepts both.
   */
  private fun detectInputFormat(file: VirtualFile): InputFormat {
    return when (file.extension?.lowercase()) {
      "yaml", "yml" -> InputFormat.YAML
      else -> InputFormat.JSON
    }
  }

  private fun buildRegistry(version: SpecificationVersion): SchemaRegistry {
    val registryConfig = SchemaRegistryConfig.builder()
      .regularExpressionFactory(JoniRegularExpressionFactory.getInstance())
      .preloadSchema(false)
      .build()
    return SchemaRegistry.builder()
      .defaultDialectId(version.dialectId)
      .schemaLoader(IntelliJSchemaLoader(project))
      .schemaRegistryConfig(registryConfig)
      .dialectRegistry(FallbackDialectRegistry(version))
      .build()
  }

}

/**
 * DialectRegistry that falls back to [fallbackVersion] when $schema URI is unrecognized.
 *
 * Standard behavior: networknt's DefaultDialectRegistry tries to download the meta-schema
 * for unknown $schema URIs (e.g. "http://json-schema.org/schema#"), which fails with
 * FileNotFoundException and kills validation silently.
 *
 * This registry: known drafts -> delegate -> fallback (no crash).
 *
 * Note: the dialectId received here is already normalized by SchemaRegistry.normalizeDialectId()
 * before being forwarded to this registry, so no additional normalization is needed.
 */
@VisibleForTesting
class FallbackDialectRegistry(
  fallbackVersion: SpecificationVersion,
  private val delegate: DialectRegistry = DefaultDialectRegistry(),
) : DialectRegistry {
  private val fallbackDialect: Dialect = Specification.getDialect(fallbackVersion)
                                         ?: throw IllegalArgumentException("No dialect for $fallbackVersion")

  override fun getDialect(dialectId: String, schemaRegistry: SchemaRegistry): Dialect {
    Specification.getDialect(dialectId)?.let { return it }
    return try {
      delegate.getDialect(dialectId, schemaRegistry)
    }
    catch (e: Exception) {
      LOG.warn(
        "Unrecognized \$schema URI '$dialectId' cannot be loaded (${e.javaClass.simpleName}: ${e.message}) — " +
        "falling back to ${fallbackDialect.id}"
      )
      fallbackDialect
    }
  }

  companion object {
    private val LOG = Logger.getInstance(FallbackDialectRegistry::class.java)
  }
}
