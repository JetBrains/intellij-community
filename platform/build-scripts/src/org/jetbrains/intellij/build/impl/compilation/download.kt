// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.intellij.build.impl.compilation

import com.intellij.util.lang.HashMapZipFile
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import kotlinx.coroutines.isActive
import okio.IOException
import org.jetbrains.intellij.build.forEachConcurrent
import org.jetbrains.intellij.build.http2Client.*
import org.jetbrains.intellij.build.io.INDEX_FILENAME
import org.jetbrains.intellij.build.telemetry.TraceManager.spanBuilder
import org.jetbrains.intellij.build.telemetry.use
import java.net.URI
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardOpenOption
import java.util.*
import java.util.concurrent.CancellationException
import java.util.concurrent.atomic.AtomicLong
import kotlin.io.path.name

internal suspend fun downloadCompilationCache(
  serverUrl: URI,
  client: Http2ClientConnectionFactory,
  toDownload: Collection<FetchAndUnpackItem>,
  downloadedBytes: AtomicLong,
  skipUnpack: Boolean,
  saveHash: Boolean,
) {
  checkMirrorAndConnect(initialServerUri = serverUrl, client = client) { connection, urlPathPrefix ->
    val zstdDecompressContextPool = ZstdDecompressContextPool()
    toDownload.forEachConcurrent(downloadParallelism) { item ->
      val urlPath = "$urlPathPrefix/${item.name}/${item.file.fileName}"
      spanBuilder("download").setAttribute("name", item.name).setAttribute("urlPath", urlPath).use { span ->
        try {
          downloadedBytes.getAndAdd(
            download(
              item = item,
              urlPath = urlPath,
              skipUnpack = skipUnpack,
              saveHash = saveHash,
              connection = connection,
              zstdDecompressContextPool = zstdDecompressContextPool,
            )
          )
        }
        catch (e: CancellationException) {
          if (coroutineContext.isActive) {
            // well, we are not canceled, only child
            throw IllegalStateException("Unexpected cancellation - action is cancelled itself", e)
          }
        }
        catch (e: Throwable) {
          span.recordException(e)
          throw CompilePartDownloadFailedError(item, e)
        }
      }
    }
  }
}

private suspend fun download(
  item: FetchAndUnpackItem,
  urlPath: CharSequence,
  skipUnpack: Boolean,
  saveHash: Boolean,
  connection: Http2ClientConnection,
  zstdDecompressContextPool: ZstdDecompressContextPool,
): Long {
  val (downloaded, digest) = connection.download(path = urlPath, file = item.file, zstdDecompressContextPool = zstdDecompressContextPool, digestFactory = { sha256() })
  val computedHash = digestToString(digest!!)
  if (computedHash != item.hash) {
    //println("actualHash  : ${computeHash(item.file)}")
    //println("expectedHash: ${item.hash}")
    //println("computedHash: $computedHash")

    val spanAttributes = Attributes.of(
      AttributeKey.stringKey("name"), item.file.name,
      AttributeKey.stringKey("expected"), item.hash,
      AttributeKey.stringKey("computed"), computedHash,
    )
    throw HashMismatchException("hash mismatch ($spanAttributes)")
  }

  if (!skipUnpack) {
    spanBuilder("unpack").setAttribute("name", item.name).use {
      unpackCompilationPartArchive(item, saveHash)
    }
  }
  return downloaded
}

internal class CompilePartDownloadFailedError(@JvmField val item: FetchAndUnpackItem, cause: Throwable) : RuntimeException(cause) {
  @Suppress("DEPRECATION")
  override fun toString(): String = "item: $item, error: ${super.toString()}"
}

internal class HashMismatchException(message: String) : IOException(message)

internal fun unpackCompilationPartArchive(item: FetchAndUnpackItem, saveHash: Boolean) {
  unpackTrustedArchive(archiveFile = item.file, outDir = item.output)
  if (saveHash) {
    // save actual hash
    Files.writeString(item.output.resolve(".hash"), item.hash)
  }
}

private val OVERWRITE_OPERATION = EnumSet.of(StandardOpenOption.WRITE, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)

private fun unpackTrustedArchive(archiveFile: Path, outDir: Path) {
  Files.createDirectories(outDir)
  HashMapZipFile.load(archiveFile).use { zipFile ->
    val createdDirs = HashSet<Path>()
    createdDirs.add(outDir)
    for (entry in zipFile.entries) {
      if (entry.isDirectory || entry.name == INDEX_FILENAME) {
        continue
      }

      val file = outDir.resolve(entry.name)
      val parent = file.parent
      if (createdDirs.add(parent)) {
        Files.createDirectories(parent)
      }

      FileChannel.open(file, OVERWRITE_OPERATION).use { channel ->
        channel.write(entry.getByteBuffer(zipFile, null))
      }
    }
  }
}