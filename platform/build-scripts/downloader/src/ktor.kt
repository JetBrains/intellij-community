// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")
package org.jetbrains.intellij.build

import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.cio.CIO
import io.ktor.client.plugins.HttpRequestRetry
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.compression.ContentEncoding
import io.ktor.client.request.get
import io.ktor.client.request.prepareGet
import io.ktor.client.statement.bodyAsChannel
import io.ktor.client.statement.bodyAsText
import io.ktor.http.HttpHeaders
import io.ktor.util.read
import io.ktor.utils.io.*
import io.ktor.utils.io.core.use
import io.ktor.utils.io.jvm.nio.copyTo
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import io.opentelemetry.api.trace.SpanBuilder
import io.opentelemetry.api.trace.StatusCode
import io.opentelemetry.context.Context
import io.opentelemetry.extension.kotlin.asContextElement
import kotlinx.coroutines.*
import org.jetbrains.intellij.build.dependencies.BuildDependenciesCommunityRoot
import org.jetbrains.intellij.build.dependencies.BuildDependenciesDownloader
import java.nio.channels.FileChannel
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.nio.file.attribute.FileTime
import java.time.Instant
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicReference
import java.util.function.Supplier
import kotlin.time.Duration.Companion.hours

const val SPACE_REPO_HOST = "packages.jetbrains.team"

private val httpClient = SynchronizedClearableLazy {
  // HttpTimeout is not used - CIO engine handles that
  HttpClient(CIO) {
    expectSuccess = true

    engine {
      requestTimeout = 2.hours.inWholeMilliseconds
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

private val httpSpaceClient = SynchronizedClearableLazy {
  httpClient.value.config {
    // we have custom error handler
    expectSuccess = false

    val token = System.getenv("SPACE_PACKAGE_TOKEN")
    if (!token.isNullOrEmpty()) {
      install(Auth) {
        bearer {
          sendWithoutRequest { request ->
            request.url.host == SPACE_REPO_HOST
          }

          loadTokens {
            BearerTokens(token, "")
          }
        }
      }
    }
    else {
      val userName = System.getProperty("jps.auth.spaceUsername")
      val password = System.getProperty("jps.auth.spacePassword")
      if (userName != null && password != null) {
        install(Auth) {
          basic {
            sendWithoutRequest { request ->
              request.url.host == SPACE_REPO_HOST
            }

            credentials {
              BasicAuthCredentials(username = userName, password = password)
            }
          }
        }
      }
    }
  }
}

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
        // set under lock to ensure that initializer is not called several times
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
    recordException(e)
    setStatus(StatusCode.ERROR)
    throw e
  }
  finally {
    end()
  }
}

// copy from util, do not make public
internal suspend inline fun <T> SpanBuilder.useWithScope2(crossinline operation: suspend (Span) -> T): T {
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
  httpSpaceClient.drop()?.close()
}

private val fileLocks = StripedMutex()

suspend fun downloadAsBytes(url: String): ByteArray {
  return spanBuilder("download").setAttribute("url", url).useWithScope2 {
    withContext(Dispatchers.IO) {
      httpClient.value.get(url).body()
    }
  }
}

suspend fun downloadAsText(url: String): String {
  return spanBuilder("download").setAttribute("url", url).useWithScope2 {
    withContext(Dispatchers.IO) {
      httpClient.value.get(url).bodyAsText()
    }
  }
}

fun downloadFileToCacheLocationSync(url: String, communityRoot: BuildDependenciesCommunityRoot): Path {
  @Suppress("RAW_RUN_BLOCKING")
  return runBlocking(Dispatchers.IO) {
    downloadFileToCacheLocation(url, communityRoot)
  }
}

suspend fun downloadFileToCacheLocation(url: String, communityRoot: BuildDependenciesCommunityRoot): Path {
  BuildDependenciesDownloader.cleanUpIfRequired(communityRoot)

  val target = BuildDependenciesDownloader.getTargetFile(communityRoot, url)
  val targetPath = target.toString()
  val lock = fileLocks.getLock(targetPath.hashCode())
  lock.lock()
  try {
    if (Files.exists(target)) {
      Span.current().addEvent("use asset from cache", Attributes.of(
        AttributeKey.stringKey("url"), url,
        AttributeKey.stringKey("target"), targetPath,
      ))

      // update file modification time to maintain FIFO caches i.e. in persistent cache folder on TeamCity agent
      Files.setLastModifiedTime(target, FileTime.from(Instant.now()))
      return target
    }

    println(" * Downloading $url")

    return spanBuilder("download").setAttribute("url", url).setAttribute("target", targetPath).useWithScope2 {
      suspendingRetryWithExponentialBackOff {
        // save to the same disk to ensure that move will be atomic and not as a copy
        val tempFile = target.parent
          .resolve("${target.fileName}-${(Instant.now().epochSecond - 1634886185).toString(36)}-${Instant.now().nano.toString(36)}".take(255))
        Files.deleteIfExists(tempFile)
        try {
          val response = httpSpaceClient.value.prepareGet(url).execute {
            coroutineScope {
              it.bodyAsChannel().copyAndClose(writeChannel(tempFile))
            }
            it
          }
          val statusCode = response.status.value
          if (statusCode != 200) {
            val builder = StringBuilder("Cannot download\n")
            val headers = response.headers
            headers.names()
              .asSequence()
              .sorted()
              .flatMap { headerName -> headers.getAll(headerName)!!.map { value -> "Header: $headerName: $value\n" } }
              .forEach(builder::append)
            builder.append('\n')
            if (Files.exists(tempFile)) {
              Files.newInputStream(tempFile).use { inputStream ->
                // yes, not trying to guess encoding
                // string constructor should be exception free,
                // so at worse we'll get some random characters
                builder.append(inputStream.readNBytes(1024).toString(StandardCharsets.UTF_8))
              }
            }
            throw BuildDependenciesDownloader.HttpStatusException(builder.toString(), statusCode, url)
          }

          val contentLength = response.headers.get(HttpHeaders.ContentLength)?.toLongOrNull() ?: -1
          check(contentLength > 0) { "Header '${HttpHeaders.ContentLength}' is missing or zero for $url" }
          val fileSize = Files.size(tempFile)
          check(fileSize == contentLength) {
            "Wrong file length after downloading uri '$url' to '$tempFile': expected length $contentLength " +
            "from Content-Length header, but got $fileSize on disk"
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

internal fun CoroutineScope.writeChannel(file: Path): ByteWriteChannel {
  return reader(CoroutineName("file-writer") + Dispatchers.IO, autoFlush = true) {
    FileChannel.open(file, WRITE_NEW_OPERATION).use { fileChannel ->
      channel.copyTo(fileChannel)
    }
  }.channel
}
