package com.intellij.platform.buildScripts.downloader

import com.intellij.platform.buildScripts.concurrency.taskScope
import org.assertj.core.api.Assertions.assertThat
import org.jetbrains.intellij.build.BuildHttpSession
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesConstants
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.CacheDirCleanup
import org.jetbrains.intellij.build.downloadAsBytes
import org.jetbrains.intellij.build.downloadAsText
import org.jetbrains.intellij.build.downloadFileToCacheLocation
import org.jetbrains.intellij.build.downloadFileWithoutCaching
import org.jetbrains.intellij.build.lastModifiedFromHeadRequest
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Timeout
import org.junit.jupiter.api.assertThrows
import org.junit.jupiter.api.io.TempDir
import org.junit.jupiter.api.parallel.ResourceLock
import org.junit.jupiter.api.parallel.Resources
import java.io.ByteArrayOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.Base64
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.zip.Deflater
import java.util.zip.DeflaterOutputStream
import java.util.zip.GZIPOutputStream
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@Timeout(30)
@ResourceLock(Resources.SYSTEM_PROPERTIES)
class DownloaderHttpTest {
  @TempDir
  lateinit var tempDir: Path

  private lateinit var root: BuildDependenciesCommunityRoot
  private var previousCache: String? = null

  @BeforeEach
  fun setUp() {
    Files.createFile(tempDir.resolve("intellij.idea.community.main.iml"))
    root = BuildDependenciesCommunityRoot(tempDir)
    previousCache = System.setProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY, tempDir.resolve("cache").toString())
  }

  @AfterEach
  fun tearDown() {
    val previous = previousCache
    if (previous == null) System.clearProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY)
    else System.setProperty(BuildDependenciesConstants.DOWNLOAD_CACHE_DIR_PROPERTY, previous)
  }

  @Test
  fun `text uses the response charset`() {
    DownloadHttpServer { exchange ->
      exchange.responseHeaders.set("Content-Type", "text/plain; charset=ISO-8859-1")
      exchange.respond("caf\u00e9".toByteArray(Charsets.ISO_8859_1))
    }.use { server ->
      assertThat(downloadAsText(server.uri.toString())).isEqualTo("caf\u00e9")
    }
  }

  @Test
  fun `byte downloads decode gzip and raw deflate`() {
    val content = "compressed response".toByteArray()
    for (encoding in listOf("gzip", "deflate")) {
      DownloadHttpServer { exchange ->
        assertThat(exchange.requestHeaders.getFirst("User-Agent")).isEqualTo("Build Script Downloader")
        exchange.responseHeaders.set("Content-Encoding", encoding)
        exchange.respond(compress(content, encoding))
      }.use { server ->
        assertThat(downloadAsBytes(server.uri.toString())).isEqualTo(content)
      }
    }
  }

  @Test
  fun `head returns the last modified header`() {
    DownloadHttpServer { exchange ->
      assertThat(exchange.requestMethod).isEqualTo("HEAD")
      exchange.responseHeaders.set("Last-Modified", "Wed, 01 Jan 2025 00:00:00 GMT")
      exchange.sendResponseHeaders(200, -1)
    }.use { server ->
      assertThat(lastModifiedFromHeadRequest(server.uri.toString())).isEqualTo("Wed, 01 Jan 2025 00:00:00 GMT")
    }
  }

  @Test
  fun `a cache hit does not fetch credentials or use the network`() {
    val requests = AtomicInteger()
    val credentials = AtomicInteger()
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      exchange.respond("cached")
    }.use { server ->
      val first = downloadFileToCacheLocation(server.uri.toString(), root)
      val second = downloadFileToCacheLocation(server.uri.toString(), root) {
        credentials.incrementAndGet()
        error("The cache must not request credentials")
      }
      assertThat(second).isEqualTo(first)
      assertThat(Files.readString(first)).isEqualTo("cached")
      assertThat(requests.get()).isEqualTo(1)
      assertThat(credentials.get()).isZero()
    }
  }

  @Test
  fun `basic authentication waits for a challenge`() {
    val authorization = CopyOnWriteArrayList<String>()
    val credentials = AtomicInteger()
    DownloadHttpServer { exchange ->
      val header = exchange.requestHeaders.getFirst("Authorization")
      authorization.add(header ?: "absent")
      if (header == null) {
        exchange.responseHeaders.set("WWW-Authenticate", "Basic realm=\"download\"")
        exchange.respond("challenge", 401)
      }
      else exchange.respond("authenticated")
    }.use { server ->
      val file = downloadFileToCacheLocation(server.uri.toString(), root) {
        credentials.incrementAndGet()
        BuildDependenciesDownloader.Credentials("username", "password")
      }
      assertThat(Files.readString(file)).isEqualTo("authenticated")
      assertThat(authorization).containsExactly("absent", "Basic ${Base64.getEncoder().encodeToString("username:password".toByteArray())}")
      assertThat(credentials.get()).isEqualTo(1)
    }
  }

  @Test
  fun `bearer authentication is preemptive`() {
    DownloadHttpServer { exchange ->
      assertThat(exchange.requestHeaders.getFirst("Authorization")).isEqualTo("Bearer token")
      exchange.respond("authenticated")
    }.use { server ->
      assertThat(Files.readString(downloadFileToCacheLocation(server.uri.toString(), root, "token"))).isEqualTo("authenticated")
    }
  }

  @Test
  fun `a redirect does not forward the bearer token to another authority`() {
    DownloadHttpServer { exchange ->
      assertThat(exchange.requestHeaders.getFirst("Authorization")).isNull()
      exchange.respond("redirected")
    }.use { destination ->
      DownloadHttpServer { exchange ->
        assertThat(exchange.requestHeaders.getFirst("Authorization")).isEqualTo("Bearer token")
        exchange.responseHeaders.set("Location", destination.uri.toString())
        exchange.respond("", 302)
      }.use { source ->
        val file = downloadFileToCacheLocation(source.uri.toString(), root, "token")
        assertThat(Files.readString(file)).isEqualTo("redirected")
      }
    }
  }

  @Test
  fun `relative redirects preserve the token`() {
    DownloadHttpServer { exchange ->
      assertThat(exchange.requestHeaders.getFirst("Authorization")).isEqualTo("Bearer token")
      if (exchange.requestURI.path == "/") {
        exchange.responseHeaders.set("Location", "/file")
        exchange.respond("", 307)
      }
      else exchange.respond("redirected")
    }.use { server ->
      assertThat(Files.readString(downloadFileToCacheLocation(server.uri.toString(), root, "token"))).isEqualTo("redirected")
    }
  }

  @Test
  fun `compressed cache responses are rejected before publication`() {
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      if (requests.incrementAndGet() == 1) {
        exchange.responseHeaders.set("Content-Encoding", "gzip")
        exchange.respond(compress("rejected".toByteArray(), "gzip"))
      }
      else exchange.respond("accepted")
    }.use { server ->
      val file = downloadFileToCacheLocation(server.uri.toString(), root)
      assertThat(Files.readString(file)).isEqualTo("accepted")
      assertThat(requests.get()).isEqualTo(2)
      assertCacheContainsOnly(file)
    }
  }

  @Test
  fun `a missing content length does not publish a cache file`() {
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      if (requests.incrementAndGet() == 1) {
        exchange.sendResponseHeaders(200, 0)
        exchange.responseBody.write("chunked".toByteArray())
      }
      else exchange.respond("accepted")
    }.use { server ->
      val file = downloadFileToCacheLocation(server.uri.toString(), root)
      assertThat(Files.readString(file)).isEqualTo("accepted")
      assertThat(requests.get()).isEqualTo(2)
      assertCacheContainsOnly(file)
    }
  }

  @Test
  fun `not found is not retried and diagnostics are bounded`() {
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      exchange.responseHeaders.set("X-Probe", "header")
      exchange.respond("x".repeat(2048) + "not included", 404)
    }.use { server ->
      val error = assertThrows<Exception> { downloadFileToCacheLocation(server.uri.toString(), root) }
      val status = generateSequence<Throwable>(error) { it.cause }.filterIsInstance<BuildDependenciesDownloader.HttpStatusException>().first()
      assertThat(status.statusCode).isEqualTo(404)
      assertThat(status.message).contains("header").doesNotContain("not included")
      assertThat(requests.get()).isEqualTo(1)
      Files.list(tempDir.resolve("cache")).use { assertThat(it.toList()).isEmpty() }
    }
  }

  @Test
  fun `parallel cache downloads publish one file with one request`() {
    val started = CountDownLatch(1)
    val release = CountDownLatch(1)
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      started.countDown()
      release.await()
      exchange.respond("shared")
    }.use { server ->
      taskScope {
        val first = fork("first cached download") { downloadFileToCacheLocation(server.uri.toString(), root) }
        assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
        val second = fork("second cached download") { downloadFileToCacheLocation(server.uri.toString(), root) }
        release.countDown()
        join()
        assertThat(first.get()).isEqualTo(second.get())
        assertThat(requests.get()).isEqualTo(1)
      }
    }
  }

  @Test
  fun `uncached downloads never overwrite an existing file`() {
    val target = tempDir.resolve("existing")
    Files.writeString(target, "original")
    DownloadHttpServer { it.respond("replacement") }.use { server ->
      assertThrows<Exception> { downloadFileWithoutCaching(server.uri.toString(), target) }
      assertThat(Files.readString(target)).isEqualTo("original")
    }
  }

  @Test
  fun `cached downloads have five total attempts`() {
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      exchange.respond("unavailable", 503)
    }.use { server ->
      BuildHttpSession(initialRetryDelay = Duration.ZERO).use { session ->
        assertThrows<Exception> { downloadFileToCacheLocation(server.uri.toString(), root, session) }
      }
      assertThat(requests.get()).isEqualTo(5)
      assertCacheIsEmpty()
    }
  }

  @Test
  fun `an interrupted body leaves no file and does not retry`() {
    val started = CountDownLatch(1)
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      exchange.sendResponseHeaders(200, 100)
      exchange.responseBody.write(0)
      exchange.responseBody.flush()
      started.countDown()
      CountDownLatch(1).await()
    }.use { server ->
      BuildHttpSession(initialRetryDelay = Duration.ZERO).use { session ->
        interruptDownload(started) { downloadFileToCacheLocation(server.uri.toString(), root, session) }
      }
      assertThat(requests.get()).isEqualTo(1)
      assertCacheIsEmpty()
    }
  }

  @Test
  fun `an interrupted retry delay does not start another attempt`() {
    val started = CountDownLatch(1)
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      exchange.respond("unavailable", 503)
      started.countDown()
    }.use { server ->
      BuildHttpSession(initialRetryDelay = 10.seconds).use { session ->
        interruptDownload(started) { downloadFileToCacheLocation(server.uri.toString(), root, session) }
      }
      assertThat(requests.get()).isEqualTo(1)
      assertCacheIsEmpty()
    }
  }

  @Test
  fun `a cache hit does not initialize the session`() {
    DownloadHttpServer { it.respond("cached") }.use { server ->
      val cached = downloadFileToCacheLocation(server.uri.toString(), root)
      BuildHttpSession().use { session ->
        session.close()
        assertThat(downloadFileToCacheLocation(server.uri.toString(), root, session)).isEqualTo(cached)
      }
    }
  }

  @Test
  fun `a closed session rejects a cache miss without retries`() {
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      exchange.respond("unexpected")
    }.use { server ->
      BuildHttpSession().use { session ->
        session.close()
        assertThrows<IllegalStateException> { downloadFileToCacheLocation(server.uri.toString(), root, session) }
      }
      assertThat(requests.get()).isZero()
      assertCacheIsEmpty()
    }
  }

  @Test
  fun `each cached attempt has its own body deadline`() {
    val requests = AtomicInteger()
    DownloadHttpServer { exchange ->
      requests.incrementAndGet()
      exchange.sendResponseHeaders(200, 100)
      exchange.responseBody.write(0)
      exchange.responseBody.flush()
      CountDownLatch(1).await()
    }.use { server ->
      BuildHttpSession(requestTimeout = 1.seconds, initialRetryDelay = Duration.ZERO).use { session ->
        assertThrows<Exception> { downloadFileToCacheLocation(server.uri.toString(), root, session) }
      }
      assertThat(requests.get()).isEqualTo(5)
      assertCacheIsEmpty()
    }
  }

  private fun interruptDownload(started: CountDownLatch, download: () -> Path) {
    val result = CompletableFuture<Boolean>()
    val caller = Thread.ofVirtual().start {
      try {
        download()
        result.completeExceptionally(AssertionError("The download did not stop"))
      }
      catch (_: InterruptedException) {
        result.complete(Thread.currentThread().isInterrupted)
      }
      catch (failure: Throwable) {
        result.completeExceptionally(failure)
      }
    }
    try {
      assertThat(started.await(5, TimeUnit.SECONDS)).isTrue()
      caller.interrupt()
      assertThat(result.get(5, TimeUnit.SECONDS)).isTrue()
    }
    finally {
      caller.interrupt()
      caller.join(5000)
      assertThat(caller.isAlive).isFalse()
    }
  }

  private fun assertCacheIsEmpty() {
    Files.list(tempDir.resolve("cache")).use { files ->
      assertThat(files.filter { it.fileName.toString() != CacheDirCleanup.LAST_CLEANUP_MARKER_FILE_NAME }.toList()).isEmpty()
    }
  }

  private fun assertCacheContainsOnly(file: Path) {
    Files.list(tempDir.resolve("cache")).use { files ->
      assertThat(files.filter { it.fileName.toString() != CacheDirCleanup.LAST_CLEANUP_MARKER_FILE_NAME }.toList()).containsExactly(file)
    }
  }
}

private fun compress(content: ByteArray, encoding: String): ByteArray {
  val buffer = ByteArrayOutputStream()
  if (encoding == "gzip") GZIPOutputStream(buffer).use { it.write(content) }
  else {
    val deflater = Deflater(Deflater.DEFAULT_COMPRESSION, true)
    try {
      DeflaterOutputStream(buffer, deflater).use { it.write(content) }
    }
    finally {
      deflater.end()
    }
  }
  return buffer.toByteArray()
}
