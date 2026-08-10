// Copyright 2000-2026 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.jetbrains.jsonSchema.remote.http

import com.intellij.ide.trustedProjects.TrustedProjects
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.util.ProgressIndicatorUtils
import com.intellij.openapi.progress.util.awaitWithCheckCanceled
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.util.messages.Topic
import com.jetbrains.jsonSchema.remote.JsonFileResolver
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import org.jetbrains.annotations.ApiStatus
import org.jetbrains.annotations.TestOnly
import java.net.URISyntaxException
import java.util.concurrent.ConcurrentHashMap

private const val REMOTE_REFS_REGISTRY_KEY = "json.schema.networknt.resolve.remote.refs"
private const val MAX_CONCURRENT_DOWNLOADS_REGISTRY_KEY = "json.schema.remote.max.concurrent.downloads"

private val LOG = logger<JsonSchemaRemoteContentService>()

/** The reason why the last download of a URL did not give a result. */
enum class RemoteDownloadFailure { Failed, Rejected }

/** Receives the download events of [JsonSchemaRemoteContentService] on the project message bus. */
@ApiStatus.Internal
fun interface RemoteSchemaDownloadListener {
  /**
   * Runs after a download ends and changes the cached state of [url] or its [JsonSchemaRemoteContentService.lastFailure].
   *
   * @param url the value of the normalized [SchemaUrl].
   */
  fun downloadFinished(url: String)

  companion object {
    @JvmField
    @Topic.ProjectLevel
    val TOPIC: Topic<RemoteSchemaDownloadListener> = Topic(RemoteSchemaDownloadListener::class.java, Topic.BroadcastDirection.NONE)
  }
}

@Service(Service.Level.PROJECT)
@ApiStatus.Internal
class JsonSchemaRemoteContentService(private val project: Project, private val scope: CoroutineScope) : RemoteSchemaContentProvider {
  private val cache: SchemaContentCache get() = service()
  private val transport: SchemaHttpTransport get() = service()

  private val inFlight = ConcurrentHashMap<SchemaUrl, Deferred<Unit>>()
  private val semaphore = Semaphore(Registry.intValue(MAX_CONCURRENT_DOWNLOADS_REGISTRY_KEY, 32).coerceAtLeast(1))
  private val lastFailures = ConcurrentHashMap<SchemaUrl, RemoteDownloadFailure>()

  override fun isAllowed(): Boolean =
    isProjectTrusted() &&
    JsonFileResolver.isRemoteEnabled(project) &&
    Registry.`is`(REMOTE_REFS_REGISTRY_KEY, true)

  // Check the disabled trust first. The language server runs headless and does not register the trusted projects extension points.
  private fun isProjectTrusted(): Boolean =
    TrustedProjects.isTrustedCheckDisabled() || TrustedProjects.isProjectTrusted(project)

  override fun getCached(url: SchemaUrl): SchemaContent? {
    if (!isAllowed()) return null
    val cached = cache.read(url) as? CachedSchema.Content ?: return null
    if (cached.isStale) prefetch(url)
    return cached.content
  }

  /** Returns cached remote schema as a local JSON file, or schedules its download on cache miss. */
  fun getCachedFile(url: SchemaUrl): VirtualFile? {
    if (!isAllowed()) return null
    when (val cached = cache.lookupFile(url)) {
      is CachedFile.Available -> {
        if (cached.isStale) prefetch(url)
        return cached.file
      }
      is CachedFile.Unavailable -> return null
      null -> Unit
    }
    prefetch(url)
    return null
  }

  fun getCachedFile(url: String): VirtualFile? = parseOrNull(url)?.let(::getCachedFile)

  fun getSchemaIri(file: VirtualFile): String {
    file.getUserData(SchemaOrigin.URL_KEY)?.let { return it }
    val recovered = (cache as? JsonSchemaFileCache)?.getOriginalUrl(file) ?: return file.url
    file.putUserData(SchemaOrigin.URL_KEY, recovered)
    return recovered
  }

  /**
   * Schedules a download of [url] unless its last download failed.
   * An automatic retry after each failure would restart the schema consumers in a loop, for example when the IDE is offline.
   * Use [retryDownload] to try a failed URL again.
   */
  override fun prefetch(url: SchemaUrl): Boolean {
    if (!isAllowed()) return false
    if (lastFailures[url] == null) scheduleDownload(url)
    return true
  }

  fun prefetch(url: String): Boolean = parseOrNull(url)?.let(::prefetch) == true

  /**
   * Clears the last failure of [url] and schedules its download again.
   * This is an explicit user request, so it also skips a cached error and revalidates fresh content.
   */
  fun retryDownload(url: SchemaUrl) {
    if (!isAllowed()) return
    lastFailures.remove(url)
    scheduleDownload(url, force = true)
  }

  fun retryDownload(url: String) {
    parseOrNull(url)?.let(::retryDownload)
  }

  /** @param force skips a cached error and revalidates fresh content. */
  private fun scheduleDownload(url: SchemaUrl, force: Boolean = false): Deferred<Unit> {
    val candidate = scope.async(start = CoroutineStart.LAZY) {
      download(url, force)
    }
    val existing = inFlight.putIfAbsent(url, candidate)
    if (existing != null) {
      candidate.cancel()
      return existing
    }
    candidate.invokeOnCompletion { inFlight.remove(url, candidate) }
    candidate.start()
    return candidate
  }

  @TestOnly
  fun hasInFlight(url: SchemaUrl): Boolean = inFlight.containsKey(url)

  fun hasInFlight(url: String): Boolean = parseOrNull(url)?.let { inFlight.containsKey(it) } == true

  fun isUnavailable(url: SchemaUrl): Boolean = cache.readStatus(url) is CachedStatus.Unavailable

  fun isUnavailable(url: String): Boolean = parseOrNull(url)?.let { isUnavailable(it) } == true

  fun lastFailure(url: SchemaUrl): RemoteDownloadFailure? = lastFailures[url]

  fun lastFailure(url: String): RemoteDownloadFailure? = parseOrNull(url)?.let { lastFailures[it] }

  /** `lookupFile` only. Does not prefetch. */
  fun peekCachedFile(url: SchemaUrl): VirtualFile? {
    if (!isAllowed()) return null
    return (cache.lookupFile(url) as? CachedFile.Available)?.file
  }

  fun peekCachedFile(url: String): VirtualFile? = parseOrNull(url)?.let(::peekCachedFile)

  /**
   * Schedules a download of [url], ignores its last failure, and waits for the result.
   * Returns the cached file right away when it is available.
   */
  @TestOnly
  fun downloadAndWaitForTest(url: SchemaUrl): VirtualFile? {
    if (!isAllowed()) return null
    when (val cached = cache.lookupFile(url)) {
      is CachedFile.Available -> {
        if (cached.isStale) prefetch(url)
        return cached.file
      }
      is CachedFile.Unavailable -> return null
      null -> Unit
    }

    val deferred = scheduleDownload(url)
    ProgressIndicatorUtils.withTimeout(TEST_DOWNLOAD_TIMEOUT_MS) {
      awaitWithCheckCanceled(deferred)
    }
    return (cache.lookupFile(url) as? CachedFile.Available)?.file
  }

  private fun notifyDownloadFinished(url: SchemaUrl) {
    if (project.isDisposed) return
    project.messageBus.syncPublisher(RemoteSchemaDownloadListener.TOPIC).downloadFinished(url.value)
  }

  private suspend fun download(url: SchemaUrl, force: Boolean) {
    try {
      downloadOrThrow(url, force)
    }
    catch (e: CancellationException) {
      throw e
    }
    catch (e: Exception) {
      // Nobody awaits the Deferred, so an exception from it is lost. Without a failure, each prefetch would start a new download.
      LOG.warn("Remote schema download failed unexpectedly for ${url.value}", e)
      recordFailure(url)
    }
  }

  private suspend fun downloadOrThrow(url: SchemaUrl, force: Boolean) {
    if (!isAllowed()) return
    val loaded = cache.load(url)
    val cached = cache.readStatus(url)
    val isFinal = when (cached) {
      is CachedStatus.Available -> !cached.isStale && !force
      is CachedStatus.Unavailable -> !force
      null -> false
    }
    if (isFinal) {
      if (loaded) notifyDownloadFinished(url)
      return
    }
    semaphore.withPermit {
      if (!isAllowed()) return
      val result = if (cached is CachedStatus.Available) {
        transport.refresh(url, cached.etag, cached.lastModified)
      }
      else {
        transport.download(url)
      }
      if (!isAllowed()) return
      when (result) {
        is DownloadResult.Success -> {
          lastFailures.remove(url)
          cache.write(url, result)
          notifyDownloadFinished(url)
        }
        is DownloadResult.Error -> {
          lastFailures.remove(url)
          cache.write(url, result)
          notifyDownloadFinished(url)
        }
        DownloadResult.NotModified -> {
          lastFailures.remove(url)
          cache.refreshTimestamp(url)
          if (cache.load(url)) notifyDownloadFinished(url)
        }
        is DownloadResult.Failed -> {
          LOG.debug("Remote schema download failed for ${url.value}", result.cause)
          recordFailure(url)
        }
        is DownloadResult.Rejected -> {
          LOG.debug("Remote schema download rejected for ${url.value}: ${result.reason}")
          lastFailures[url] = RemoteDownloadFailure.Rejected
          notifyDownloadFinished(url)
        }
      }
    }
  }

  private fun recordFailure(url: SchemaUrl) {
    lastFailures[url] = RemoteDownloadFailure.Failed
    notifyDownloadFinished(url)
  }

  companion object {
    private const val TEST_DOWNLOAD_TIMEOUT_MS = 10_000L

    @JvmStatic
    fun parseOrNull(url: String): SchemaUrl? = try {
      SchemaUrl.parse(url)
    }
    catch (_: IllegalArgumentException) {
      null
    }
    catch (_: URISyntaxException) {
      null
    }

    @JvmStatic
    fun isValidUrl(url: String): Boolean = parseOrNull(url) != null

    @JvmStatic
    fun getInstance(project: Project): JsonSchemaRemoteContentService = project.service()
  }
}
