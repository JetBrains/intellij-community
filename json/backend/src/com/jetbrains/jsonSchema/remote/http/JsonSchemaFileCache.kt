// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.platform.eel.fs.EelFiles
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.progress.withLockMaybeCancellable
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import org.jetbrains.annotations.VisibleForTesting
import java.io.IOException
import java.net.URI
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.locks.ReentrantLock
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Instant

/**
 * NIO store for downloaded remote schemas (`json-schema-cache/<hash>.json` + `.meta`), with an in-memory layer in front of it.
 *
 * The read side ([read], [readStatus], [lookupFile]) uses the memory only. It takes no lock, does no IO, and is safe under
 * the read lock. A memory miss is the same as a schema that is not downloaded: the caller schedules a download, and the
 * download job calls [load] first.
 *
 * The write side ([load], [write], [refreshTimestamp]) does the disk IO, the VFS adoption and the memory update under the
 * stripe of the URL. It must run without read access. The readers never take a stripe, so a sync VFS refresh under a stripe
 * cannot deadlock with a reader that holds the read lock.
 *
 * The schema bytes come from the adopted [VirtualFile], so the VFS content cache holds them. An entry without an adopted
 * file is a miss for [read] and [lookupFile].
 */
@ApiStatus.Internal
class JsonSchemaFileCache : SchemaContentCache {
  private val root: Path = cacheRoot()
  private val locks = StripedMutex()
  private val entries = ConcurrentHashMap<SchemaUrl, Entry>()

  override fun read(url: SchemaUrl): CachedSchema? = when (val entry = liveEntry(url)) {
    is Entry.Available -> {
      val file = entry.file?.takeIf { it.isValid }
      if (file == null) null
      else try {
        CachedSchema.Content(SchemaContent(file.contentsToByteArray()), entry.metadata.etag, entry.metadata.lastModified, isStale(entry))
      }
      catch (e: IOException) {
        logger<JsonSchemaFileCache>().debug("Unable to read the cached remote schema ${file.path}", e)
        null
      }
    }
    is Entry.Unavailable -> entry.schema
    null -> null
  }

  override fun readStatus(url: SchemaUrl): CachedStatus? = when (val entry = liveEntry(url)) {
    is Entry.Available -> entry.status(isStale(entry))
    is Entry.Unavailable -> entry.status
    null -> null
  }

  override fun lookupFile(url: SchemaUrl): CachedFile? = when (val entry = liveEntry(url)) {
    is Entry.Available -> if (entry.file?.isValid == true) entry.cachedFile(isStale(entry)) else null
    is Entry.Unavailable -> entry.cachedFile
    null -> null
  }

  override fun load(url: SchemaUrl): Boolean {
    ThreadingAssertions.assertNoReadAccess()
    return locks.getLock(url).withLockMaybeCancellable {
      val previous = entries[url]
      val entry = loadFromDisk(url)
      if (entry == null) entries.remove(url) else entries[url] = entry
      entry != previous
    }
  }

  override fun write(url: SchemaUrl, result: DownloadResult) {
    ThreadingAssertions.assertNoReadAccess()
    locks.getLock(url).withLockMaybeCancellable {
      val status = when (result) {
        is DownloadResult.Success -> CacheMetadata.Status.OK
        is DownloadResult.Error -> CacheMetadata.Status.Error(result.code, result.message)
        else -> return@withLockMaybeCancellable
      }
      Files.createDirectories(root)
      val key = digestKey(url)
      val paths = paths(url, (result as? DownloadResult.Success)?.finalUrl)
      for (extension in listOf("json", "yaml")) {
        val stale = root.resolve("$key.$extension")
        if (stale != paths.content) Files.deleteIfExists(stale)
      }
      val metadata = CacheMetadata(
        url = url.value,
        status = status,
        timestamp = Clock.System.now().toEpochMilliseconds(),
        etag = (result as? DownloadResult.Success)?.etag,
        lastModified = (result as? DownloadResult.Success)?.lastModified,
        finalUrl = (result as? DownloadResult.Success)?.finalUrl,
      )
      if (result is DownloadResult.Success) {
        writeAtomically(paths.content, result.content.bytes)
      }
      else {
        Files.deleteIfExists(paths.content)
      }
      writeAtomically(paths.metadata, json.encodeToString(metadata).toByteArray(StandardCharsets.UTF_8))
      entries[url] = when (status) {
        CacheMetadata.Status.OK -> Entry.Available(metadata, paths.content, adopt(paths.content, metadata.url, metadata.finalUrl!!, reloadContent = true))
        is CacheMetadata.Status.Error -> Entry.Unavailable(metadata, status.code, status.message)
      }
    }
  }

  fun getOriginalUrl(file: VirtualFile): String? {
    val path = try {
      Path.of(file.path)
    }
    catch (e: Exception) {
      logger<JsonSchemaFileCache>().debug("Unable to convert cached schema path ${file.path} to a local path", e)
      return null
    }
    if (path.parent != root) return null
    entries.values.firstOrNull { it is Entry.Available && it.file == file }?.let { return it.metadata.finalUrl ?: it.metadata.url }
    ThreadingAssertions.assertNoReadAccess()
    val metadata = root.resolve(path.fileName.toString().substringBeforeLast('.') + ".meta")
    if (!Files.isRegularFile(metadata)) return null
    return try {
      val parsed = json.decodeFromString<CacheMetadata>(EelFiles.readString(metadata))
      parsed.finalUrl ?: parsed.url
    }
    catch (e: Exception) {
      logger<JsonSchemaFileCache>().debug("Unable to recover the origin URL for ${file.url}", e)
      null
    }
  }

  override fun refreshTimestamp(url: SchemaUrl) {
    ThreadingAssertions.assertNoReadAccess()
    locks.getLock(url).withLockMaybeCancellable {
      val rejectedPaths = paths(url)
      if (!Files.isRegularFile(rejectedPaths.metadata)) return@withLockMaybeCancellable
      val metadata = try {
        json.decodeFromString<CacheMetadata>(EelFiles.readString(rejectedPaths.metadata))
      }
      catch (e: Exception) {
        logger<JsonSchemaFileCache>().debug("Discarding corrupt remote schema cache entry for ${url.value}", e)
        reject(rejectedPaths)
        entries.remove(url)
        return@withLockMaybeCancellable
      }
      val paths = paths(url, metadata.finalUrl)
      if (!Files.isRegularFile(paths.content) || metadata.url != url.value || metadata.status != CacheMetadata.Status.OK) return@withLockMaybeCancellable
      val refreshed = metadata.copy(timestamp = Clock.System.now().toEpochMilliseconds())
      writeAtomically(paths.metadata, json.encodeToString(refreshed).toByteArray(StandardCharsets.UTF_8))
      entries.computeIfPresent(url) { _, entry ->
        if (entry is Entry.Available && entry.content == paths.content) entry.copy(metadata = refreshed) else entry
      }
    }
  }

  /** The URL that served the cached content of [url]. Uses the memory only. */
  @VisibleForTesting
  fun getRetrievalUrl(url: SchemaUrl): String? = entries[url]?.metadata?.let { it.finalUrl ?: it.url }

  /** Drops the in-memory layer, so that only [load] makes the disk entries visible again. */
  @TestOnly
  fun clearMemoryForTest() {
    entries.clear()
  }

  private fun liveEntry(url: SchemaUrl): Entry? {
    val entry = entries[url] ?: return null
    return if (entry is Entry.Unavailable && age(entry.metadata) > errorTtl) null else entry
  }

  private fun isStale(entry: Entry.Available): Boolean = age(entry.metadata) > contentTtl

  private fun age(metadata: CacheMetadata): Duration = Clock.System.now() - Instant.fromEpochMilliseconds(metadata.timestamp)

  /** Reads and validates the disk entry of [url]. Deletes an invalid entry. */
  private fun loadFromDisk(url: SchemaUrl): Entry? {
    val metadata = readMetadata(url) ?: return rejectIfMetadataPresent(paths(url))
    val paths = paths(url, metadata.finalUrl)
    if (metadata.url != url.value) return reject(paths)
    return when (val status = metadata.status) {
      CacheMetadata.Status.OK -> {
        if (metadata.finalUrl == null || !Files.isRegularFile(paths.content)) return reject(paths)
        Entry.Available(metadata, paths.content, adopt(paths.content, metadata.url, metadata.finalUrl, reloadContent = false))
      }
      is CacheMetadata.Status.Error ->
        if (age(metadata) > errorTtl) reject(paths) else Entry.Unavailable(metadata, status.code, status.message)
    }
  }

  /**
   * Makes [content] known to the VFS and loads its bytes into the VFS content cache.
   *
   * @param reloadContent refreshes a file that the VFS already knows, because `refreshAndFind` does not reload its content.
   */
  private fun adopt(content: Path, requestUrl: String, retrievalUrl: String, reloadContent: Boolean): VirtualFile? {
    ThreadingAssertions.assertNoReadAccess()
    val file = LocalFileSystem.getInstance().refreshAndFindFileByNioFile(content) ?: return null
    if (reloadContent) {
      VfsUtil.markDirtyAndRefresh(false, false, false, file)
    }
    file.putUserData(SchemaOrigin.URL_KEY, retrievalUrl)
    file.putUserData(SchemaOrigin.REQUEST_URL_KEY, requestUrl)
    return try {
      file.contentsToByteArray()
      file
    }
    catch (e: IOException) {
      logger<JsonSchemaFileCache>().debug("Unable to load the cached remote schema $content into the VFS", e)
      null
    }
  }

  private fun readMetadata(url: SchemaUrl): CacheMetadata? {
    ThreadingAssertions.assertNoReadAccess()
    val metadata = root.resolve("${digestKey(url)}.meta")
    if (!Files.isRegularFile(metadata)) return null
    return try {
      json.decodeFromString<CacheMetadata>(EelFiles.readString(metadata))
    }
    catch (e: Exception) {
      logger<JsonSchemaFileCache>().debug("Corrupt remote schema cache metadata for ${url.value}", e)
      null
    }
  }

  private fun reject(paths: CachePaths): Nothing? {
    ThreadingAssertions.assertNoReadAccess()
    val key = paths.metadata.fileName.toString().removeSuffix(".meta")
    Files.deleteIfExists(root.resolve("$key.json"))
    Files.deleteIfExists(root.resolve("$key.yaml"))
    Files.deleteIfExists(paths.metadata)
    return null
  }

  private fun rejectIfMetadataPresent(paths: CachePaths): Nothing? {
    ThreadingAssertions.assertNoReadAccess()
    if (Files.isRegularFile(paths.metadata)) reject(paths)
    return null
  }

  private fun paths(url: SchemaUrl): CachePaths = paths(url, null)

  private fun paths(url: SchemaUrl, retrievalUrl: String?): CachePaths {
    val key = digestKey(url)
    return CachePaths(root.resolve("$key.${contentExtension(retrievalUrl ?: url.value)}"), root.resolve("$key.meta"))
  }

  private fun digestKey(url: SchemaUrl): String =
    MessageDigest.getInstance("SHA-256").digest(url.value.toByteArray(StandardCharsets.UTF_8)).joinToString("") { "%02x".format(it) }

  private fun contentExtension(url: String): String {
    val fileName = try {
      URI(url).path?.substringAfterLast('/').orEmpty()
    }
    catch (e: Exception) {
      logger<JsonSchemaFileCache>().debug("Unable to infer the cached schema extension from $url", e)
      ""
    }
    return when (fileName.substringAfterLast('.', "").lowercase()) {
      "yaml", "yml" -> "yaml"
      else -> "json"
    }
  }

  private fun writeAtomically(target: Path, content: ByteArray) {
    ThreadingAssertions.assertNoReadAccess()
    val temp = target.resolveSibling(target.fileName.toString() + ".tmp")
    try {
      Files.write(temp, content)
      atomicReplace(temp, target)
    }
    finally {
      Files.deleteIfExists(temp)
    }
  }

  private fun atomicReplace(source: Path, target: Path) {
    try {
      Files.move(source, target, ATOMIC_MOVE, REPLACE_EXISTING)
    }
    catch (_: AtomicMoveNotSupportedException) {
      Files.move(source, target, REPLACE_EXISTING)
    }
  }

  private data class CachePaths(val content: Path, val metadata: Path)

  /** An immutable memory entry. It keeps the ready results, so a lookup only compares the age with the TTL. */
  private sealed interface Entry {
    val metadata: CacheMetadata

    data class Available(override val metadata: CacheMetadata, val content: Path, val file: VirtualFile?) : Entry {
      private val freshStatus = CachedStatus.Available(false, metadata.etag, metadata.lastModified)
      private val staleStatus = freshStatus.copy(isStale = true)
      private val freshFile = file?.let { CachedFile.Available(it, false) }
      private val staleFile = freshFile?.copy(isStale = true)

      fun status(isStale: Boolean): CachedStatus = if (isStale) staleStatus else freshStatus
      fun cachedFile(isStale: Boolean): CachedFile? = if (isStale) staleFile else freshFile
    }

    data class Unavailable(override val metadata: CacheMetadata, val code: Int, val message: String) : Entry {
      val status: CachedStatus = CachedStatus.Unavailable(code, message)
      val cachedFile: CachedFile = CachedFile.Unavailable(code, message)
      val schema: CachedSchema = CachedSchema.Unavailable(code, message)
    }
  }

  private val contentTtl: Duration get() = Registry.intValue("json.schema.remote.cache.stale.hours", 4).hours
  private val errorTtl: Duration get() = Registry.intValue("json.schema.remote.cache.error.hours", 4).hours

  companion object {
    private val json = Json { ignoreUnknownKeys = true }

    @TestOnly
    fun rootForTest(): Path = cacheRoot()
  }
}

private fun cacheRoot(): Path = Path.of(PathManager.getSystemPath(), "json-schema-cache")

@Serializable
private data class CacheMetadata(
  val url: String,
  val status: Status,
  val timestamp: Long,
  val etag: String? = null,
  val lastModified: String? = null,
  val finalUrl: String? = null,
) {
  @Serializable
  sealed class Status {
    @Serializable
    data object OK : Status()

    @Serializable
    data class Error(val code: Int, val message: String) : Status()
  }
}

private class StripedMutex(stripeCount: Int = 32) {
  private val locks = Array(stripeCount) { ReentrantLock() }
  private val mask = stripeCount - 1

  init {
    require(stripeCount > 0) { "Stripe count must be positive" }
    require(stripeCount and mask == 0) { "Stripe count must be a power of two" }
  }

  fun getLock(url: SchemaUrl): ReentrantLock = locks[url.value.hashCode() and Int.MAX_VALUE and mask]
}
