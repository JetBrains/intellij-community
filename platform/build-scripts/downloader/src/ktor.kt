// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build

import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.prepareGet
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.copyAndClose
import io.ktor.utils.io.copyTo
import io.ktor.utils.io.core.use
import io.ktor.utils.io.jvm.nio.writeSuspendSession
import io.ktor.utils.io.reader
import io.ktor.utils.io.writer
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.CoroutineName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader.Credentials
import java.io.IOException
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.EnumSet
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.time.Duration.Companion.hours

const val SPACE_REPO_HOST: String = "packages.jetbrains.team"

private val httpClient = SynchronizedClearableLazy {
  // HttpTimeout is not used - CIO engine handles that
  HttpClient(OkHttp) {
    expectSuccess = true

    engine {
      clientCacheSize = 0
    }

    install(HttpTimeout) {
      requestTimeoutMillis = 2.hours.inWholeMilliseconds
    }

    install(ContentEncoding) {
      deflate(1.0F)
      gzip(0.9F)
    }

    install(HttpRequestRetry) {
      retryOnExceptionOrServerErrors(maxRetries = 3)
      exponentialDelay()
    }

    install(UserAgent) {
      agent = "Build Script Downloader"
    }
  }
}

fun createSubClient(block: HttpClientConfig<*>.() -> Unit): HttpClient = httpClient.get().config(block)

// copy from util, do not make public
private class SynchronizedClearableLazy<T>(private val initializer: () -> T) : Lazy<T>, Supplier<T> {
  private val computedValue = AtomicReference(notYetInitialized())

  @Suppress("UNCHECKED_CAST")
  private fun notYetInitialized(): T = NOT_YET_INITIALIZED as T

  private fun nullize(t: T): T? = if (isInitialized(t)) t else null

  private fun isInitialized(t: T?): Boolean = t !== NOT_YET_INITIALIZED

  companion object {
    private val NOT_YET_INITIALIZED = object {
      override fun toString(): String = "Not yet initialized"
    }
  }

  override fun get(): T = value

  override var value: T
    get() {
      val currentValue = computedValue.get()
      if (isInitialized(currentValue)) {
        return currentValue
      }

      // do not call initializer in parallel
      synchronized(this) {
        // set under lock to ensure that the initializer is not called several times
        return computedValue.updateAndGet { old ->
          if (isInitialized(old)) old else initializer()
        }
      }
    }
    set(value) {
      computedValue.set(value)
    }

  override fun isInitialized() = isInitialized(computedValue.get())

  override fun toString() = computedValue.toString()

  fun drop(): T? = nullize(computedValue.getAndSet(notYetInitialized()))
}

// copy from util, do not make public
internal inline fun <T> Span.use(operation: (Span) -> T): T {
  try {
    return operation(this)
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    end()
  }
}

// copy from util, do not make public
internal suspend inline fun <T> SpanBuilder.useWithScope(crossinline operation: suspend (Span) -> T): T {
  val span = startSpan()
  return withContext(Context.current().with(span).asContextElement()) {
    span.use {
      operation(span)
    }
  }
}

internal fun spanBuilder(spanName: String): SpanBuilder = BuildDependenciesDownloader.TRACER.spanBuilder(spanName)

fun closeKtorClient() {
  httpClient.drop()?.close()
}

private val fileLocks = StripedMutex()

suspend fun downloadAsBytes(url: String): ByteArray = spanBuilder("download").setAttribute("url", url).useWithScope {
  withContext(Dispatchers.IO) {
    httpClient.value.get(url).body()
  }
}

suspend fun postData(url: String, data: ByteArray): Unit = withContext(Dispatchers.IO) {
  httpClient.value.post(url) {
    setBody(data)
  }
}

suspend fun downloadAsText(url: String): String {
  return spanBuilder("download").setAttribute("url", url).useWithScope {
    withContext(Dispatchers.IO) {
      httpClient.value.get(url).bodyAsText()
    }
  }
}

fun downloadFileToCacheLocationSync(url: String, communityRoot: BuildDependenciesCommunityRoot): Path {
  return runBlocking(Dispatchers.IO) {
    downloadFileToCacheLocation(url, communityRoot)
  }
}

fun downloadFileToCacheLocationSync(url: String, communityRoot: BuildDependenciesCommunityRoot, credentialsProvider: () -> Credentials): Path = runBlocking(Dispatchers.IO) {
  downloadFileToCacheLocation(url, communityRoot, credentialsProvider)
}

suspend fun downloadFileToCacheLocation(url: String, communityRoot: BuildDependenciesCommunityRoot): Path {
  return downloadFileToCacheLocation(url = url, communityRoot = communityRoot, token = null, credentialsProvider = null)
}

suspend fun downloadFileToCacheLocation(url: String, communityRoot: BuildDependenciesCommunityRoot, token: String): Path {
  return downloadFileToCacheLocation(url = url, communityRoot = communityRoot, token = token, credentialsProvider = null)
}

suspend fun downloadFileToCacheLocation(
  url: String,
  communityRoot: BuildDependenciesCommunityRoot,
  credentialsProvider: () -> Credentials,
): Path {
  return downloadFileToCacheLocation(url = url, communityRoot = communityRoot, token = null, credentialsProvider = credentialsProvider)
}

private suspend fun downloadFileToCacheLocation(
  url: String,
  communityRoot: BuildDependenciesCommunityRoot,
  token: String?,
  credentialsProvider: (() -> Credentials)?,
): Path {
  BuildDependenciesDownloader.cleanUpIfRequired(communityRoot)

  val target = BuildDependenciesDownloader.getTargetFile(communityRoot, url)
  val targetPath = target.toString()
  val lock = fileLocks.getLock(targetPath)
  lock.lock()
  try {
    if (Files.exists(target)) {
      Span.current().addEvent("use asset from cache", Attributes.of(
        AttributeKey.stringKey("url"), url,
        AttributeKey.stringKey("target"), targetPath,
      ))

      // update file modification time to maintain FIFO caches, i.e., in a persistent cache dir on TeamCity agent
      try {
        Files.setLastModifiedTime(target, FileTime.from(Instant.now()))
      } catch (e: IOException) {
        Span.current().addEvent("update asset file modification time failed: $e")
      }
      return target
    }

    println(" * Downloading $url")

    return spanBuilder("download").setAttribute("url", url).setAttribute("target", targetPath).useWithScope {
      retryWithExponentialBackOff {
        // save to the same disk to ensure that move will be atomic and not as a copy
        val tempFile = target.parent
          .resolve("${target.fileName}-${(Instant.now().epochSecond - 1634886185).toString(36)}-${Instant.now().nano.toString(36)}".take(255))
        Files.deleteIfExists(tempFile)
        try {
          // each io.ktor.client.HttpClient.config call creates a new client
          // extract common configuration to prevent excessive client creation
          val commonConfig: HttpClientConfig<*>.() -> Unit = {
            expectSuccess = false // we have custom error handler

            install(ContentEncoding) {
              // Any `Content-Encoding` will drop the "Content-Length" header in nginx responses,
              // yet we rely on that header in `downloadFileToCacheLocation`.
              // Hence, we override `ContentEncoding` plugin config from `httpClient` with zero weights.
              deflate(0.0F)
              gzip(0.0F)
              identity(1.0F)
            }
          }

          val response = getEffectiveClient(token = token, commonConfig = commonConfig, credentialsProvider = credentialsProvider).use { client ->
            doDownloadFileWithoutCaching(client = client, url = url, file = tempFile)
          }

          val statusCode = response.status.value
          val headers = response.headers
          if (statusCode != 200) {
            val builder = StringBuilder("Cannot download\n")
            headers.names()
              .asSequence()
              .sorted()
              .flatMap { headerName -> headers.getAll(headerName)!!.map { value -> "Header: $headerName: $value\n" } }
              .forEach(builder::append)
            builder.append('\n')
            if (Files.exists(tempFile)) {
              Files.newInputStream(tempFile).use { inputStream ->
                // yes, not trying to guess encoding
                // string constructor should be exception-free, so at worse, we'll get some random characters
                builder.append(inputStream.readNBytes(1024).toString(StandardCharsets.UTF_8))
              }
            }
            throw BuildDependenciesDownloader.HttpStatusException(builder.toString(), statusCode, url)
          }

          val contentLength = headers.get(HttpHeaders.ContentLength)?.toLongOrNull() ?: -1
          check(contentLength > 0) { "Header '${HttpHeaders.ContentLength}' is missing or zero for $url" }
          val contentEncoding = headers.get(HttpHeaders.ContentEncoding)
          if (contentEncoding != null && contentEncoding != "identity") {
            // There's a `Content-Encoding` in response while we explicitly asked the server not to use it.
            // We cannot compare `Content-Length` with local file size,
            // so we rely only on the fact that the encoder would've thrown an exception when decoding an incorrect body.
          }
          else {
            val fileSize = Files.size(tempFile)
            check(fileSize == contentLength) {
              "Wrong file length after downloading uri '$url' to '$tempFile': expected length $contentLength " +
              "from ${HttpHeaders.ContentLength} header, but got $fileSize on disk"
            }
          }
          Files.move(tempFile, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
        }
        finally {
          Files.deleteIfExists(tempFile)
        }
      }

      target
    }
  }
  finally {
    lock.unlock()
  }
}

private fun getEffectiveClient(
  token: String?,
  commonConfig: HttpClientConfig<*>.() -> Unit,
  credentialsProvider: (() -> Credentials)?,
): HttpClient {
  return when {
    token != null -> httpClient.value.config {
      commonConfig()
      Auth {
        bearer {
          loadTokens {
            BearerTokens(token, "")
          }
        }
      }
    }
    credentialsProvider != null -> httpClient.value.config {
      commonConfig()
      Auth {
        basic {
          credentials {
            sendWithoutRequest { true }
            val credentials = credentialsProvider()
            BasicAuthCredentials(credentials.username, credentials.password)
          }
        }
      }
    }

    else -> httpClient.value.config(commonConfig)
  }
}

suspend fun downloadFileWithoutCaching(url: String, tempFile: Path) {
  doDownloadFileWithoutCaching(httpClient.get(), url, tempFile)
}

private suspend fun doDownloadFileWithoutCaching(client: HttpClient, url: String, file: Path): HttpResponse {
  return client.prepareGet(url).execute {
    coroutineScope {
      it.bodyAsChannel().copyAndClose(writeChannel(file))
    }
    it
  }
}

fun CoroutineScope.readChannel(file: Path): ByteReadChannel {
  return writer(CoroutineName("file-reader") + Dispatchers.IO, autoFlush = false) {
    FileChannel.open(file, StandardOpenOption.READ).use { fileChannel ->
      @Suppress("DEPRECATION")
      channel.writeSuspendSession {
        while (true) {
          val buffer = request(1)
          if (buffer == null) {
            channel.flush()
            tryAwait(1)
            continue
          }

          val rc = fileChannel.read(buffer)
          if (rc == -1) {
            break
          }
          written(rc)
        }
      }
    }
  }.channel
}

private val WRITE_NEW_OPERATION = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE_NEW)

private fun CoroutineScope.writeChannel(file: Path): ByteWriteChannel {
  return reader(CoroutineName("file-writer") + Dispatchers.IO, autoFlush = true) {
    FileChannel.open(file, WRITE_NEW_OPERATION).use { fileChannel ->
      channel.copyTo(fileChannel)
    }
  }.channel
}
