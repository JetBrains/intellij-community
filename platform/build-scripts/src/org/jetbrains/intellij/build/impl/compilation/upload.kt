// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
@file:Suppress("ReplaceGetOrSet")

package org.jetbrains.intellij.build.impl.compilation

import io.netty.handler.codec.http.HttpHeaderValues
import io.netty.handler.codec.http.HttpResponseStatus
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import kotlinx.serialization.Serializable
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.http2Client.Http2ClientConnection
import org.jetbrains.intellij.build.http2Client.ZstdCompressContextPool
import org.jetbrains.intellij.build.http2Client.post
import org.jetbrains.intellij.build.http2Client.upload
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.nio.file.Files
import java.util.concurrent.CancellationException
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.LongAdder

internal suspend fun uploadArchives(
  reportStatisticValue: (key: String, value: String) -> Unit,
  config: CompilationCacheUploadConfiguration,
  metadataJson: String,
  httpConnection: Http2ClientConnection,
  items: List<PackAndUploadItem>,
) {
  val uploadedCount = LongAdder()
  val uploadedBytes = LongAdder()
  val uncompressedBytes = LongAdder()
  val reusedCount = LongAdder()
  val reusedBytes = LongAdder()
  val start = System.nanoTime()

  var fallbackToHeads = false
  val alreadyUploaded: Set<String> = try {
    if (config.checkFiles) {
      spanBuilder("fetch info about already uploaded files").use {
        getFoundAndMissingFiles(metadataJson = metadataJson, urlPathPrefix = config.serverUrlPathPrefix, connection = httpConnection).found
      }
    }
    else {
      emptySet()
    }
  }
  catch (e: CancellationException) {
    throw e
  }
  catch (e: Throwable) {
    Span.current().recordException(e, Attributes.of(AttributeKey.stringKey("message"), "failed to fetch info about already uploaded files, will fallback to HEAD requests"))
    fallbackToHeads = true
    emptySet()
  }

  val urlPathPrefix = "${config.serverUrlPathPrefix}/${config.uploadUrlPathPrefix}"
  val zstdCompressContextPool = ZstdCompressContextPool()
  items.forEachConcurrent(uploadParallelism) { item ->
    if (alreadyUploaded.contains(item.name)) {
      reusedCount.increment()
      reusedBytes.add(Files.size(item.archive))
      return@forEachConcurrent
    }

    val urlPath = "$urlPathPrefix/${item.name}/${item.hash!!}.jar"
    spanBuilder("upload archive").setAttribute("name", item.name).setAttribute("hash", item.hash!!).use { span ->
      if (fallbackToHeads) {
        val status = httpConnection.head(urlPath)
        if (status == HttpResponseStatus.OK) {
          span.addEvent("already exist on server, nothing to upload", Attributes.of(AttributeKey.stringKey("urlPath"), urlPath))

          reusedCount.increment()
          val size = Files.size(item.archive)
          reusedBytes.add(size)
          uncompressedBytes.add(size)
          return@use
        }
        else if (status != HttpResponseStatus.NOT_FOUND) {
          span.addEvent(
            "responded with unexpected",
            Attributes.of(
              AttributeKey.stringKey("status"), status.toString(),
              AttributeKey.stringKey("urlPath"), urlPath,
            ),
          )
        }
      }

      // Using ZSTD dictionary doesn't make the difference, even slightly worse (default compression level 3).
      // That's because in our case, we compress a relatively large archive of class files.
      val result = httpConnection.upload(path = urlPath, file = item.archive, zstdCompressContextPool = zstdCompressContextPool)
      require(result.fileSize > 0)
      uncompressedBytes.add(result.fileSize)
      uploadedCount.increment()
      uploadedBytes.add(result.uploadedSize)
    }
  }

  val totalUploadedBytes = uploadedBytes.sum()
  val totalUncompressedBytes = uncompressedBytes.sum()
  val totalReusedBytes = reusedBytes.sum()
  Span.current().addEvent(
    "upload complete",
    Attributes.of(
      AttributeKey.longKey("reusedParts"), reusedCount.sum(),
      AttributeKey.longKey("uploadedParts"), uploadedCount.sum(),

      AttributeKey.longKey("reusedBytes"), totalReusedBytes,
      AttributeKey.longKey("uploadedBytes"), totalUploadedBytes,

      AttributeKey.longKey("totalBytes"), totalReusedBytes + totalUncompressedBytes,
      AttributeKey.longKey("totalCount"), (reusedCount.sum() + uploadedCount.sum()),
    )
  )

  reportStatisticValue("compile-parts:uncompressedBytes:bytes", totalUncompressedBytes.toString())
  reportStatisticValue("compile-parts:reused:bytes", totalReusedBytes.toString())
  reportStatisticValue("compile-parts:reused:count", reusedCount.sum().toString())

  reportStatisticValue("compile-parts:uploaded:bytes", totalUploadedBytes.toString())
  reportStatisticValue("compile-parts:uploaded:count", uploadedCount.sum().toString())
  reportStatisticValue("compile-parts:uploaded:time", TimeUnit.NANOSECONDS.toMillis((System.nanoTime() - start)).toString())

  reportStatisticValue("compile-parts:total:bytes", (totalReusedBytes + totalUncompressedBytes).toString())
  reportStatisticValue("compile-parts:total:count", (reusedCount.sum() + uploadedCount.sum()).toString())
}

private suspend fun getFoundAndMissingFiles(metadataJson: String, urlPathPrefix: String, connection: Http2ClientConnection): CheckFilesResponse {
  return connection.post(path = "$urlPathPrefix/check-files", data = metadataJson, contentType = HttpHeaderValues.APPLICATION_JSON)
}

@Serializable
private data class CheckFilesResponse(
  @JvmField val found: HashSet<String> = HashSet(),
)
