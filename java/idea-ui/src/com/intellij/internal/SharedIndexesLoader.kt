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
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.io.FileUtil
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.indexing.IndexInfrastructureVersion
import com.intellij.util.io.HttpRequests
import org.jetbrains.annotations.TestOnly
import org.jetbrains.concurrency.AsyncPromise
import org.jetbrains.concurrency.Promise
import org.jetbrains.concurrency.compute
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream
import java.io.File
import java.io.IOException
import java.nio.file.Path

private object SharedIndexesCdn {
  private const val innerVersion = "v1"

  private val indexesCdnUrl
    get() = Registry.stringValue("shared.indexes.url").trimEnd('/')

  fun hashIndexUrl(request: SharedIndexRequest) = request.run {
    "$indexesCdnUrl/$innerVersion/$kind/$hash/index.json.xz"
  }
}

data class SharedIndexRequest(
  val kind: String,
  val hash: String
)

data class SharedIndexInfo(
  val url: String,
  val sha256: String,
  val metadata: ObjectNode
)

data class SharedIndexResult(
  val request: SharedIndexRequest,
  val url: String,
  val sha256: String,
  val version: IndexInfrastructureVersion
) {
  override fun toString(): String = "SharedIndex sha=${sha256.take(8)}, wv=${version.weakVersionHash.take(8)}"
}

class SharedIndexesLoader {
  private val LOG = logger<SharedIndexesLoader>()

  companion object {
    @JvmStatic
    fun getInstance() = service<SharedIndexesLoader>()
  }

  fun lookupIndexes(project: Project,
                    request: SharedIndexRequest): Promise<Path?> {
    val promise = AsyncPromise<Path?>()
    ProgressManager.getInstance().run(object : Task.Backgroundable(project,
                                                                   "Looking for Shared Indexes",
                                                                   true,
                                                                   PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        promise.compute {
          val info  = lookupSharedIndex(request, indicator) ?: return@compute null
          val version = info.version

          val targetFile = selectIndexFileDestination(request, version)
          downloadSharedIndex(info, indicator, targetFile)
          targetFile.toPath()
        }
      }
    })
    return promise
  }

  fun lookupSharedIndex(request: SharedIndexRequest,
                        indicator: ProgressIndicator): SharedIndexResult? {
    indicator.text = "Looking for Shared Indexes..."
    val entries = downloadIndexesList(request, indicator)
    indicator.checkCanceled()

    indicator.text = "Inspecting Shared Indexes..."
    val ourVersion = IndexInfrastructureVersion.globalVersion()
    val best = SharedIndexMetadata.selectBestSuitableIndex(ourVersion, entries)
    if (best == null) {
      LOG.info("No matching index found $request")
      return null
    }

    LOG.info("Selected index ${best.first} for $request")
    return SharedIndexResult(request, best.first.url, best.first.sha256, best.second)
  }

  fun downloadSharedIndex(info: SharedIndexResult,
                          indicator: ProgressIndicator,
                          targetFile: File) {
    val downloadFile = selectDownloadIndexFileDestination(info.request, info.version)
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

  private fun selectNewFileName(request: SharedIndexRequest,
                                version: IndexInfrastructureVersion,
                                basePath: File,
                                ext: String): File {
    var infix = 0
    while (true) {
      val name = request.kind + "-" + request.hash + "-" + version.weakVersionHash + "-" + infix + ext
      val resultFile = File(basePath, name)
      if (!resultFile.isFile) {
        resultFile.parentFile?.mkdirs()
        return resultFile
      }
      infix++
    }
  }

  private fun selectIndexFileDestination(request: SharedIndexRequest,
                                         version: IndexInfrastructureVersion) =
    selectNewFileName(request, version, File(PathManager.getSystemPath(), "shared-indexes"), ".ijx")


  private fun selectDownloadIndexFileDestination(request: SharedIndexRequest,
                                                 version: IndexInfrastructureVersion) =
    selectNewFileName(request, version, File(PathManager.getTempPath()), ".ijx.xz")


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

  @TestOnly
  fun downloadIndexesList(request: SharedIndexRequest,
                          indicator: ProgressIndicator?
  ): List<SharedIndexInfo> {
    val indexUrl = SharedIndexesCdn.hashIndexUrl(request)
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

