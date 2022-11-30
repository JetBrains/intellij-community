// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.github.luben.zstd.ZstdDirectBufferDecompressingStreamNoFinalizer
import com.intellij.diagnostic.telemetry.forkJoinTask
import com.intellij.diagnostic.telemetry.useWithScope
import com.intellij.util.lang.HashMapZipFile
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.Span
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import okio.IOException
import org.jetbrains.intellij.build.TraceManager.spanBuilder
import org.jetbrains.intellij.build.io.INDEX_FILENAME
import java.net.HttpURLConnection
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.security.MessageDigest
import java.util.*
import java.util.concurrent.ForkJoinTask
import kotlin.io.path.name

private val OVERWRITE_OPERATION = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

internal fun downloadCompilationCache(serverUrl: String,
                                      prefix: String,
                                      toDownload: List<FetchAndUnpackItem>,
                                      client: OkHttpClient,
                                      bufferPool: DirectFixedSizeByteBufferPool,
                                      saveHash: Boolean): List<FetchAndUnpackItem> {
  var urlWithPrefix = "$serverUrl/$prefix/"
  // first let's check for initial redirect (mirror selection)
  spanBuilder("mirror selection").useWithScope { span ->
    client.newCall(Request.Builder()
                     .url(urlWithPrefix)
                     .head()
                     .build()).execute().use { response ->
      val statusCode = response.code
      val locationHeader = response.header("location")
      if ((statusCode == HttpURLConnection.HTTP_MOVED_TEMP ||
           statusCode == HttpURLConnection.HTTP_MOVED_PERM ||
           statusCode == 307 ||
           statusCode == HttpURLConnection.HTTP_SEE_OTHER)
          && locationHeader != null) {
        urlWithPrefix = locationHeader
        span.addEvent("redirected to mirror", Attributes.of(AttributeKey.stringKey("url"), urlWithPrefix))
      }
      else {
        span.addEvent("origin server will be used", Attributes.of(AttributeKey.stringKey("url"), urlWithPrefix))
      }
    }
  }

  return ForkJoinTask.invokeAll(toDownload.map { item ->
    val url = "${urlWithPrefix}${item.name}/${item.file.fileName}"
    forkJoinTask(spanBuilder("download").setAttribute("name", item.name).setAttribute("url", url)) {
      client.newCall(Request.Builder()
                       .url(url)
                       .build()).execute().use { response ->
        if (response.isSuccessful) {
          val digest = sha256()
          writeFile(item.file, response, bufferPool, url, digest)
          val computedHash = digestToString(digest)
          if (computedHash == item.hash) {
            null
          }
          else {
            Span.current().addEvent("hash mismatch", Attributes.of(
              AttributeKey.stringKey("name"), item.file.name,
              AttributeKey.stringKey("expected"), item.hash,
              AttributeKey.stringKey("computed"), computedHash,
            ))
            return@forkJoinTask item
          }
        }
        else {
          Span.current().addEvent("cannot download", Attributes.of(
            AttributeKey.stringKey("name"), item.file.name,
            AttributeKey.stringKey("url"), url,
            AttributeKey.longKey("statusCode"), response.code.toLong(),
          ))
          return@forkJoinTask item
        }
      }

      spanBuilder("unpack").setAttribute("name", item.name).useWithScope {
        unpackArchive(item, saveHash)
      }
      null
    }
  }).mapNotNull { it.rawResult }
}

internal fun unpackArchive(item: FetchAndUnpackItem, saveHash: Boolean) {
  HashMapZipFile.load(item.file).use { zipFile ->
    val root = item.output
    Files.createDirectories(root)
    val createdDirs = HashSet<Path>()
    createdDirs.add(root)
    for (entry in zipFile.entries) {
      if (entry.isDirectory || entry.name == INDEX_FILENAME) {
        continue
      }

      val file = root.resolve(entry.name)
      val parent = file.parent
      if (createdDirs.add(parent)) {
        Files.createDirectories(parent)
      }

      FileChannel.open(file, OVERWRITE_OPERATION).use { channel ->
        channel.write(entry.getByteBuffer(zipFile), 0)
      }
    }
  }

  if (saveHash) {
    // save actual hash
    Files.writeString(item.output.resolve(".hash"), item.hash)
  }
}

private fun writeFile(file: Path, response: Response, bufferPool: DirectFixedSizeByteBufferPool, url: String, digest: MessageDigest) {
  Files.createDirectories(file.parent)
  FileChannel.open(file, OVERWRITE_OPERATION).use { channel ->
    val source = response.body.source()
    val sourceBuffer = bufferPool.allocate()
    object : ZstdDirectBufferDecompressingStreamNoFinalizer(sourceBuffer) {
      public override fun refill(toRefill: ByteBuffer): ByteBuffer {
        toRefill.clear()
        do {
          if (source.read(toRefill) == -1) {
            break
          }
        }
        while (!source.exhausted() && toRefill.hasRemaining())
        toRefill.flip()
        return toRefill
      }

      override fun close() {
        try {
          super.close()
        }
        finally {
          bufferPool.release(sourceBuffer)
        }
      }
    }.use { decompressor ->
      var offset = 0L
      val targetBuffer = bufferPool.allocate()
      try {
        // refill is not called on start
        decompressor.refill(sourceBuffer)
        do {
          do {
            // decompressor can consume not the whole source buffer if target buffer size is not enough
            decompressor.read(targetBuffer)
            targetBuffer.flip()

            targetBuffer.mark()
            digest.update(targetBuffer)
            targetBuffer.reset()

            do {
              offset += channel.write(targetBuffer, offset)
            }
            while (targetBuffer.hasRemaining())
            targetBuffer.clear()
          }
          while (sourceBuffer.hasRemaining())
        }
        while (!source.exhausted())
      }
      catch (e: IOException) {
        throw IOException("Cannot unpack $url", e)
      }
      finally {
        bufferPool.release(targetBuffer)
      }
    }
  }
}

