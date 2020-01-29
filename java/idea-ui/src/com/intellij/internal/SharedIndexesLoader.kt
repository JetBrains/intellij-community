// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ArrayNode
import com.fasterxml.jackson.databind.node.ObjectNode
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.progress.PerformInBackgroundOption
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.registry.Registry
import com.intellij.util.io.HttpRequests
import org.tukaani.xz.XZInputStream
import java.io.ByteArrayInputStream

private object SharedIndexesCdn {
  private const val innerVersion = "v1"

  private val indexesCdnUrl
    get() = Registry.stringValue("shared.indexes.url").trimEnd('/')

  fun hashIndexUrl(kind: String, hash: String)
    = "$indexesCdnUrl/$innerVersion/$kind/$hash/index.json.xz"

}

data class SharedIndexInfo(
  val url: String,
  val sha256: String,
  val metadata: ObjectNode
)

class SharedIndexesLoader {
  private val LOG = logger<SharedIndexesLoader>()

  companion object {
    @JvmStatic fun getInstance() = service<SharedIndexesLoader>()
  }

  fun lookupIndexes(project: Project?,
                  kind: String,
                  sourceHash: String,
                  callback: (ProgressIndicator, List<SharedIndexInfo>) -> Unit) {

    ProgressManager.getInstance().run(object: Task.Backgroundable(project, "Looking for Shared Indexes", true, PerformInBackgroundOption.ALWAYS_BACKGROUND) {
      override fun run(indicator: ProgressIndicator) {
        indicator.text = "Looking for Shared Indexes..."

        val indexUrl = SharedIndexesCdn.hashIndexUrl(kind, sourceHash)
        LOG.info("Checking index at $indexUrl...")

        val rawDataXZ = try {
          HttpRequests.request(indexUrl).readBytes(indicator)
        } catch (e: HttpRequests.HttpStatusException) {
          LOG.info("No indexes available for hash $sourceHash. ${e.message}",e )
          return
        }

        val rawData = try {
          ByteArrayInputStream(rawDataXZ).use { input ->
            XZInputStream(input).use {
              it.readBytes()
            }
          }
        } catch (e: Exception) {
          LOG.warn("Failed to unpack index data for hash $sourceHash. ${e.message}", e)
          return
        }

        LOG.info("Downloaded the $indexUrl...")

        val json = try {
          ObjectMapper().readTree(rawData)
        } catch (e: Exception) {
          LOG.warn("Failed to read index data JSON for hash $sourceHash. ${e.message}", e)
          return
        }

        val listVersion = json.get("list_version")?.asText()
        if (listVersion != "1") {
          LOG.warn("Index data version mismatch. Current version is $listVersion")
          return
        }

        val entries = (json.get("entries") as? ArrayNode) ?: run {
          LOG.warn("Index data format is incomplete. Missing 'entries' element")
          return
        }

        val indexes = entries.elements().asSequence().mapNotNull { node ->
          if (node !is ObjectNode) return@mapNotNull null

          val url = node.get("url")?.asText() ?: return@mapNotNull null
          val sha = node.get("sha256")?.asText() ?: return@mapNotNull null
          val data = (node.get("metadata") as? ObjectNode) ?: return@mapNotNull null

          SharedIndexInfo(url, sha, data)
        }.toList()

        LOG.info("Detected ${indexes.size} batches for the hash $sourceHash")

        if (indexes.isEmpty()) {
          LOG.info("No indexes found for $sourceHash")
          return
        }

        callback(indicator, indexes)
      }
    })
  }
}

