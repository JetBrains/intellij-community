// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import org.jetbrains.annotations.ApiStatus
import java.net.URI

@JvmInline
@ApiStatus.Internal
value class SchemaUrl private constructor(val value: String) {
  companion object {
    @JvmStatic
    fun parse(value: String): SchemaUrl {
      val uri = URI(JsonFileResolver.replaceUnsafeSchemaStoreUrls(value)!!).normalize()
      val scheme = uri.scheme?.lowercase()
      val host = uri.host?.lowercase()
      val port = when {
        scheme == "http" && uri.port == 80 -> -1
        scheme == "https" && uri.port == 443 -> -1
        else -> uri.port
      }
      val normalized = if (host != null) {
        buildString {
          append(scheme).append("://")
          uri.rawUserInfo?.let { append(it).append('@') }
          append(host)
          if (port != -1) append(':').append(port)
          append(uri.rawPath.ifEmpty { "/" })
          uri.rawQuery?.let { append('?').append(it) }
        }
      }
      else {
        if (scheme == null) uri.rawSchemeSpecificPart else "$scheme:${uri.rawSchemeSpecificPart}"
      }
      return SchemaUrl(URI(normalized).toASCIIString())
    }
  }
}

@JvmInline
@ApiStatus.Internal
value class SchemaContent(val bytes: ByteArray)

@ApiStatus.Internal
/**
 * [Error] is a cacheable negative result (e.g., 404) for a non-retriable response or policy violation;
 * [Rejected] means the initial URL is invalid or unsupported and no request is sent, nothing to cache;
 * [Failed] is a request or response failure (e.g., 500) that may be retried and must not be cached;
 */
sealed interface DownloadResult {
  data class Success(
    val content: SchemaContent,
    val etag: String?,
    val lastModified: String?,
    val finalUrl: String,
  ) : DownloadResult

  data class Error(val code: Int, val message: String) : DownloadResult {
    companion object {
      val OVERSIZED_CONTENT: Error = Error(-1, "Remote schema exceeds the configured size limit")
      val REDIRECT_LIMIT_EXCEEDED: Error = Error(-2, "Remote schema redirect limit exceeded")
      val FORBIDDEN_REDIRECT_TARGET: Error = Error(-3, "Remote schema redirected to a forbidden URL")
      val INVALID_REDIRECT_TARGET: Error = Error(-4, "Invalid remote schema redirect")
    }
  }

  data object NotModified : DownloadResult
  data class Rejected(val reason: String) : DownloadResult
  data class Failed(val cause: Throwable) : DownloadResult
}

@ApiStatus.Internal
object SchemaOrigin {
  /** The URL that served the content of a cached schema file. It is the final URL after redirects. */
  @JvmField
  val URL_KEY: Key<String> = Key.create("json.schema.origin.url")

  /** The normalized [SchemaUrl] that the IDE requested for a cached schema file. */
  @JvmField
  val REQUEST_URL_KEY: Key<String> = Key.create("json.schema.origin.request.url")

  /** Returns `true` when [file] is the cached content of the remote schema [url]. */
  @JvmStatic
  fun isCachedContentOf(file: VirtualFile, url: String?): Boolean {
    if (url == null) return false
    val requestUrl = file.getUserData(REQUEST_URL_KEY) ?: return false
    return requestUrl == JsonSchemaRemoteContentService.parseOrNull(url)?.value
  }
}

@ApiStatus.Internal
sealed interface CachedSchema {
  data class Content(
    val content: SchemaContent,
    val etag: String? = null,
    val lastModified: String? = null,
    val isStale: Boolean = false,
  ) : CachedSchema

  data class Unavailable(val code: Int, val message: String) : CachedSchema
}

@ApiStatus.Internal
sealed interface CachedStatus {
  data class Available(val isStale: Boolean, val etag: String?, val lastModified: String?) : CachedStatus
  data class Unavailable(val code: Int, val message: String) : CachedStatus
}

@ApiStatus.Internal
sealed interface CachedFile {
  data class Available(val file: VirtualFile, val isStale: Boolean) : CachedFile
  data class Unavailable(val code: Int, val message: String) : CachedFile
}

@ApiStatus.Internal
interface SchemaContentCache {
  /** Returns the cached schema of [url] from the memory. Does no IO and is safe under the read lock. */
  fun read(url: SchemaUrl): CachedSchema?

  /** Returns the cached state of [url] from the memory. Does no IO and is safe under the read lock. */
  fun readStatus(url: SchemaUrl): CachedStatus? = when (val cached = read(url)) {
    is CachedSchema.Content -> CachedStatus.Available(cached.isStale, cached.etag, cached.lastModified)
    is CachedSchema.Unavailable -> CachedStatus.Unavailable(cached.code, cached.message)
    null -> null
  }

  /** Returns the cached file of [url] from the memory. Does no IO and is safe under the read lock. */
  fun lookupFile(url: SchemaUrl): CachedFile? = null

  /**
   * Reads the persistent entry of [url] into the memory. Does IO, so the caller must not hold read access.
   *
   * @return `true` when the memory entry of [url] changed.
   */
  fun load(url: SchemaUrl): Boolean = false

  /** Stores [result] for [url]. Does IO, so the caller must not hold read access. */
  fun write(url: SchemaUrl, result: DownloadResult)

  /** Marks the cached content of [url] as fresh. Does IO, so the caller must not hold read access. */
  fun refreshTimestamp(url: SchemaUrl) {}
}

@ApiStatus.Internal
interface RemoteSchemaContentProvider {
  fun isAllowed(): Boolean
  fun getCached(url: SchemaUrl): SchemaContent?
  fun prefetch(url: SchemaUrl): Boolean
}

@ApiStatus.Internal
interface SchemaHttpTransport {
  suspend fun download(url: SchemaUrl): DownloadResult

  suspend fun refresh(
    url: SchemaUrl,
    etag: String?,
    lastModified: String?,
  ): DownloadResult = download(url)
}
