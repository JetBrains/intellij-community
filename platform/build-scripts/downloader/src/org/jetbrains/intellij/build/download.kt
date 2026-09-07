package org.jetbrains.intellij.build

import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.Credentials
import org.jetbrains.intellij.build.dependencies.BuildDependenciesExtractOptions
import org.jetbrains.intellij.build.dependencies.PreloadedDownloads
import org.jetbrains.intellij.build.dependencies.archiveCacheKey
import org.jetbrains.intellij.build.dependencies.extractToCacheLocation
import java.io.IOException
import java.net.SocketException
import java.net.URI
import java.net.http.HttpRequest
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.concurrent.CancellationException

const val SPACE_REPO_HOST: String = "packages.jetbrains.team"

internal inline fun <T> Span.use(operation: (Span) -> T): T {
  try {
    return operation(this)
  }
  catch (failure: CancellationException) {
    throw failure
  }
  catch (failure: Throwable) {
    setStatus(StatusCode.ERROR)
    throw failure
  }
  finally {
    end()
  }
}

/** Runs [operation] with a current span on the calling thread. */
internal inline fun <T> SpanBuilder.useWithScope(operation: (Span) -> T): T {
  val span = startSpan()
  return span.makeCurrent().use { span.use(operation) }
}

internal fun spanBuilder(spanName: String): SpanBuilder = BuildDependenciesDownloader.TRACER.spanBuilder(spanName)

private val fileLocks = StripedLock()

@JvmOverloads
fun downloadAsBytes(url: String, session: BuildHttpSession? = null): ByteArray {
  return withBuildHttpSession(session) { client ->
    spanBuilder("download").setAttribute("url", url).useWithScope {
      client.consume(HttpRequest.newBuilder(URI(url)).GET().build(), attempts = 4) { response ->
        if (response.statusCode() !in 200..299) throw httpStatusException(response)
        decodedHttpBody(response).use { it.readAllBytes() }
      }
    }
  }
}

@JvmOverloads
fun lastModifiedFromHeadRequest(url: String, session: BuildHttpSession? = null): String? {
  return withBuildHttpSession(session) { client ->
    spanBuilder("last-modified").setAttribute("url", url).useWithScope {
      client.send(HttpRequest.newBuilder(URI(url)).HEAD().build(), attempts = 4).requireSuccess().headers.firstValue("Last-Modified").orElse(null)
    }
  }
}

@JvmOverloads
fun downloadAsText(url: String, session: BuildHttpSession? = null): String {
  return withBuildHttpSession(session) { client ->
    spanBuilder("download").setAttribute("url", url).useWithScope {
      client.send(HttpRequest.newBuilder(URI(url)).GET().build(), attempts = 4).requireSuccess().body
    }
  }
}

@Deprecated("downloadFileToCacheLocation blocks too", ReplaceWith("downloadFileToCacheLocation(url, communityRoot)"))
fun downloadFileToCacheLocationSync(url: String, communityRoot: BuildDependenciesCommunityRoot): Path {
  return downloadFileToCacheLocation(url, communityRoot)
}

@Deprecated("downloadFileToCacheLocation blocks too", ReplaceWith("downloadFileToCacheLocation(url, communityRoot, credentialsProvider)"))
fun downloadFileToCacheLocationSync(url: String, communityRoot: BuildDependenciesCommunityRoot, credentialsProvider: () -> Credentials): Path {
  return downloadFileToCacheLocation(url, communityRoot, credentialsProvider)
}

/** Returns a cached dependency, or downloads it on a virtual thread. Blocks the calling thread. */
@JvmOverloads
fun downloadFileToCacheLocation(url: String, communityRoot: BuildDependenciesCommunityRoot, session: BuildHttpSession? = null): Path {
  return downloadFileToCacheLocation(url, communityRoot, session, authentication = null)
}

/** Contains a file and the digest of its preloaded input. Network downloads have no declared digest. */
data class ResolvedDownload(@JvmField val file: Path, @JvmField val sha256: String?)

/** Returns a read-only preloaded input directly, or downloads a writable copy into the cache. */
@JvmOverloads
fun resolveFileForReading(url: String, communityRoot: BuildDependenciesCommunityRoot, session: BuildHttpSession? = null): ResolvedDownload {
  val preloaded = PreloadedDownloads.findByUrl(url)
  if (preloaded != null) return ResolvedDownload(file = preloaded.source, sha256 = preloaded.sha256)
  return ResolvedDownload(file = downloadFileToCacheLocation(url, communityRoot, session), sha256 = null)
}

fun resolveAndExtractToCacheLocation(url: String, communityRoot: BuildDependenciesCommunityRoot, vararg options: BuildDependenciesExtractOptions): Path {
  return resolveAndExtractToCacheLocation(url, communityRoot, null, *options)
}

/** Uses the declared digest as the extraction key when a preloaded input provides one. */
fun resolveAndExtractToCacheLocation(
  url: String,
  communityRoot: BuildDependenciesCommunityRoot,
  session: BuildHttpSession?,
  vararg options: BuildDependenciesExtractOptions,
): Path {
  val resolved = resolveFileForReading(url, communityRoot, session)
  return extractToCacheLocation(
    archiveFile = resolved.file,
    communityRoot = communityRoot,
    cacheKey = archiveCacheKey(archiveFile = resolved.file, sha256 = resolved.sha256),
    options = options,
  )
}

@JvmOverloads
fun downloadFileToCacheLocation(url: String, communityRoot: BuildDependenciesCommunityRoot, token: String, session: BuildHttpSession? = null): Path {
  return downloadFileToCacheLocation(url, communityRoot, session, BuildHttpAuthentication.Bearer(token))
}

fun downloadFileToCacheLocation(url: String, communityRoot: BuildDependenciesCommunityRoot, credentialsProvider: () -> Credentials): Path {
  return downloadFileToCacheLocation(url, communityRoot, null, credentialsProvider)
}

fun downloadFileToCacheLocation(
  url: String,
  communityRoot: BuildDependenciesCommunityRoot,
  session: BuildHttpSession?,
  credentialsProvider: () -> Credentials,
): Path {
  return downloadFileToCacheLocation(url, communityRoot, session, BuildHttpAuthentication.Basic(credentialsProvider))
}

private fun downloadFileIsRetryAllowed(failure: Exception): Boolean {
  return failure !is SocketException || failure.message?.contains("Operation not permitted") != true
}

private fun downloadFileToCacheLocation(
  url: String,
  communityRoot: BuildDependenciesCommunityRoot,
  session: BuildHttpSession?,
  authentication: BuildHttpAuthentication?,
): Path {
  return withBuildHttpSession(session) { client ->
    BuildDependenciesDownloader.cleanUpIfRequired(communityRoot)
    val preloaded = PreloadedDownloads.findByUrl(url)
    val target = BuildDependenciesDownloader.getTargetFile(communityRoot, url, preloaded?.sha256)
    val targetPath = target.toString()
    fileLocks.withLock(targetPath) {
      if (Files.exists(target)) {
        Span.current().addEvent(
          "use asset from cache", Attributes.of(
            AttributeKey.stringKey("url"), url,
            AttributeKey.stringKey("target"), targetPath,
          )
        )
        try {
          Files.setLastModifiedTime(target, FileTime.from(Instant.now()))
        }
        catch (failure: IOException) {
          Span.current().addEvent("update asset file modification time failed: $failure")
        }
        return@withLock target
      }
      if (preloaded != null) {
        val temporary = Files.createTempFile(target.parent, target.fileName.toString(), ".preloaded.tmp")
        try {
          Files.copy(preloaded.source, temporary, StandardCopyOption.REPLACE_EXISTING)
          checkHttpInterrupted()
          Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        finally {
          Files.deleteIfExists(temporary)
        }
        return@withLock target
      }
      client.checkOpen()
      System.err.println(" * Downloading $url")
      spanBuilder("download").setAttribute("url", url).setAttribute("target", targetPath).useWithScope {
        retryWithExponentialBackOff(
          attempts = 5, initialDelayMs = client.initialRetryDelay.inWholeMilliseconds,
          isRetryAllowed = ::downloadFileIsRetryAllowed
        ) {
          val temporary = Files.createTempFile(target.parent, target.fileName.toString().take(180), ".download.tmp")
          try {
            val request = HttpRequest.newBuilder(URI(url)).header("Accept-Encoding", "deflate;q=0.0,gzip;q=0.0,identity;q=1.0").GET().build()
            client.consume(request, authentication) { response ->
              if (response.statusCode() != 200) throw httpStatusException(response)
              check(response.headers().firstValue("Content-Encoding").isEmpty) { "Content-Encoding is not allowed for cached downloads: $url" }
              val contentLength = response.headers().firstValue("Content-Length").orElse(null)?.toLongOrNull() ?: -1
              check(contentLength > 0) { "Header 'Content-Length' is missing or zero for $url" }
              Files.newOutputStream(temporary, StandardOpenOption.WRITE, StandardOpenOption.TRUNCATE_EXISTING).use { output ->
                response.body().copyTo(output)
              }
              val fileSize = Files.size(temporary)
              check(fileSize == contentLength) { "Wrong file length after downloading '$url': expected $contentLength, got $fileSize" }
            }
            checkHttpInterrupted()
            Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
          }
          finally {
            Files.deleteIfExists(temporary)
          }
        }
        target
      }
    }
  }
}

@JvmOverloads
fun downloadFileWithoutCaching(url: String, tempFile: Path, session: BuildHttpSession? = null) {
  withBuildHttpSession(session) { client ->
    client.consume(HttpRequest.newBuilder(URI(url)).GET().build(), attempts = 4, retryBodyFailures = false) { response ->
      if (response.statusCode() !in 200..299) throw httpStatusException(response)
      Files.newOutputStream(tempFile, StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE).use { output ->
        decodedHttpBody(response).use { it.copyTo(output) }
      }
    }
  }
}
