// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.hash.Hashing
import com.google.common.io.Files
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.*
import com.intellij.openapi.roots.OrderEntry
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.indexing.IndexInfrastructureVersion
import com.intellij.util.indexing.provided.SharedIndexChunkLocator
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.TestOnly
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Path

private object SharedIndexesCdn {
  private const val innerVersion = "v1"

  private val indexesCdnUrl
    get() = Registry.stringValue("shared.indexes.url").trimEnd('/')

  fun hashIndexUrls(request: SharedIndexRequest) = sequence {
    request.run {
      if (hash != null) {
        yield("$indexesCdnUrl/$innerVersion/$kind/$hash/index.json.xz")
      }

      for (alias in request.aliases) {
        yield("$indexesCdnUrl/$innerVersion/$kind/-$alias/index.json.xz")
      }

      //TODO[jo] remove prefix!
      for (alias in request.aliases) {
        yield("$indexesCdnUrl/$innerVersion/$kind/-$kind-$alias/index.json.xz")
      }
    }
  }.toList().distinct()
}

data class SharedIndexRequest(
  val kind: String,
  val hash: String? = null,
  val aliases: List<String> = listOf()
)

data class SharedIndexInfo(
  val url: String,
  val sha256: String,
  val metadata: ObjectNode
)

data class SharedIndexResult(
  val kind: String,
  val url: String,
  val sha256: String,
  val version: IndexInfrastructureVersion
) {
  val chunkUniqueId get() = "$kind-$sha256-${version.weakVersionHash}"

  override fun toString(): String = "SharedIndex sha=${sha256.take(8)}, wv=${version.weakVersionHash.take(8)}"
}

fun SharedIndexResult.toChunkDescriptor(sdkEntries: Collection<OrderEntry>) = object: SharedIndexChunkLocator.ChunkDescriptor {
  override fun getChunkUniqueId() = this@toChunkDescriptor.chunkUniqueId
  override fun getSupportedInfrastructureVersion() = this@toChunkDescriptor.version
  override fun getOrderEntries() = sdkEntries

  override fun downloadChunk(targetFile: Path, indicator: ProgressIndicator) {
    SharedIndexesLoader.getInstance().downloadSharedIndex(this@toChunkDescriptor, indicator, targetFile.toFile())
  }
}

class SharedIndexesLoader {
  private val LOG = logger<SharedIndexesLoader>()

  companion object {
    @JvmStatic
    fun getInstance() = service<SharedIndexesLoader>()
  }

  fun lookupSharedIndex(request: SharedIndexRequest,
                        indicator: ProgressIndicator): SharedIndexResult? {
    indicator.text = "Looking for Shared Indexes..."
    val ourVersion = IndexInfrastructureVersion.getIdeVersion()

    for (entries in downloadIndexesList(request, indicator)) {
      indicator.checkCanceled()
      if (entries.isEmpty()) continue
      indicator.pushState()

      try {
        indicator.text = "Inspecting Shared Indexes..."
        val best = SharedIndexMetadata.selectBestSuitableIndex(ourVersion, entries)
        if (best == null) {
          LOG.info("No matching index found $request")
          continue
        }

        val (info, metadata) = best
        LOG.info("Selected index ${info} for $request")
        return SharedIndexResult(request.kind, info.url, info.sha256, metadata)
      }
      finally {
        indicator.popState()
      }
    }

    return null
  }

  fun downloadSharedIndex(info: SharedIndexResult,
                          indicator: ProgressIndicator,
                          targetFile: File) {
    val downloadFile = selectNewFileName(info, info.version, File(PathManager.getTempPath()), ".ijx.xz")
    indicator.text = "Downloading Shared Index..."
    indicator.checkCanceled()
    try {
      downloadSharedIndexXZ(info, downloadFile, indicator)

      indicator.text = "Unpacking Shared Index..."
      indicator.checkCanceled()
      unpackSharedIndexXZ(targetFile, downloadFile)
    }
    catch (t: Throwable) {
      FileUtil.delete(targetFile)
      throw t
    }
    finally {
      FileUtil.delete(downloadFile)
    }
  }

  private fun selectNewFileName(info: SharedIndexResult,
                                version: IndexInfrastructureVersion,
                                basePath: File,
                                ext: String): File {
    var infix = 0
    while (true) {
      val name = info.kind + "-" + info.sha256 + "-" + version.weakVersionHash + "-" + infix + ext
      val resultFile = File(basePath, name)
      if (!resultFile.isFile) {
        resultFile.parentFile?.mkdirs()
        return resultFile
      }
      infix++
    }
  }

  fun selectIndexFileDestination(info: SharedIndexResult,
                                 version: IndexInfrastructureVersion) =
    selectNewFileName(info, version, File(PathManager.getSystemPath(), "shared-indexes"), ".ijx")


  @TestOnly
  fun downloadSharedIndexXZ(info: SharedIndexResult,
                            downloadFile: File,
                            indicator: ProgressIndicator?) {
    val url = info.url
    indicator?.pushState()
    indicator?.isIndeterminate = false

    try {
      try {
        HttpRequests.request(url)
          .productNameAsUserAgent()
          .saveToFile(downloadFile, indicator)
      }
      catch (t: IOException) {
        throw RuntimeException("Failed to download JDK from $url. ${t.message}", t)
      }

      val actualHashCode = Files.asByteSource(downloadFile).hash(Hashing.sha256()).toString()
      if (!actualHashCode.equals(info.sha256, ignoreCase = true)) {
        throw RuntimeException("SHA-256 checksums does not match. Actual value is $actualHashCode, expected ${info.sha256}")
      }
    }
    catch (e: Exception) {
      FileUtil.delete(downloadFile)
      if (e !is ProcessCanceledException) throw e
    } finally {
      indicator?.popState()
    }
  }

  @TestOnly
  fun unpackSharedIndexXZ(targetFile: File, downloadFile: File) {
    val bufferSize = 1024 * 1024
    targetFile.parentFile?.mkdirs()
    targetFile.outputStream().use { out ->
      downloadFile.inputStream().buffered(bufferSize).use { input ->
        XZInputStream(input).use { it.copyTo(out, bufferSize) }
      }
    }
  }

  fun downloadIndexesList(request: SharedIndexRequest,
                          indicator: ProgressIndicator?
  ): Sequence<List<SharedIndexInfo>> {
    val urls = SharedIndexesCdn.hashIndexUrls(request)

    return urls.asSequence().mapIndexedNotNull { idx, indexUrl ->
      indicator?.pushState()

      indicator?.text = when {
        urls.size > 1 -> "Looking for shared indexes (${idx + 1} of ${urls.size})"
        else -> "Looking for shared indexes"
      }
      val result = downloadIndexList(request, indexUrl, indicator)

      indicator?.popState()
      result
    }
  }

  private fun downloadIndexList(request: SharedIndexRequest,
                                indexUrl: String,
                                indicator: ProgressIndicator?) : List<SharedIndexInfo> {
    LOG.info("Checking index at $indexUrl...")

    val rawDataXZ = try {
      HttpRequests.request(indexUrl).throwStatusCodeException(true).readBytes(indicator)
    }
    catch (e: HttpRequests.HttpStatusException) {
      LOG.info("No indexes available for $request. ${e.message}", e)
      return listOf()
    }

    val rawData = try {
      ByteArrayInputStream(rawDataXZ).use { input ->
        XZInputStream(input).use {
          it.readBytes()
        }
      }
    }
    catch (e: Exception) {
      LOG.warn("Failed to unpack index data for $request. ${e.message}", e)
      return listOf()
    }

    LOG.info("Downloaded the $indexUrl...")

    val json = try {
      ObjectMapper().readTree(rawData)
    }
    catch (e: Exception) {
      LOG.warn("Failed to read index data JSON for $request. ${e.message}", e)
      return listOf()
    }

    val listVersion = json.get("list_version")?.asText()
    if (listVersion != "1") {
      LOG.warn("Index data version mismatch. The current version is $listVersion")
      return listOf()
    }

    val entries = (json.get("entries") as? ArrayNode) ?: run {
      LOG.warn("Index data format is incomplete. Missing 'entries' element")
      return listOf()
    }

    val indexes = entries.elements().asSequence().mapNotNull { node ->
      if (node !is ObjectNode) return@mapNotNull null

      val url = node.get("url")?.asText() ?: return@mapNotNull null
      val sha = node.get("sha256")?.asText() ?: return@mapNotNull null
      val data = (node.get("metadata") as? ObjectNode) ?: return@mapNotNull null

      SharedIndexInfo(url, sha, data)
    }.toList()

    LOG.info("Detected ${indexes.size} batches for the $request")

    if (indexes.isEmpty()) {
      LOG.info("No indexes found $request")
    }

    return indexes
  }
}

