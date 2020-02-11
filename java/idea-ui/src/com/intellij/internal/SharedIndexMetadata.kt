// Copyright 2000-2020 JetBrains s.r.o. Use of this source code is governed by the Apache 2.0 license that can be found in the LICENSE file.
package com.intellij.internal

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.google.common.collect.ImmutableSortedMap
import com.intellij.openapi.application.ApplicationInfo
import com.intellij.openapi.util.SystemInfo
import com.intellij.util.indexing.IndexInfrastructureVersion
import java.util.*
import kotlin.collections.HashMap

object SharedIndexMetadata {
  private const val METADATA_VERSION = "2"

  fun selectBestSuitableIndex(ourVersion: IndexInfrastructureVersion,
                              candidates: List<SharedIndexInfo>): Pair<SharedIndexInfo, IndexInfrastructureVersion>? {
    val nodes = candidates
      .mapNotNull{
        if (it.metadata["metadata_version"]?.asText() != METADATA_VERSION) return@mapNotNull null

        val baseVersions = it.metadata.mapFromPath("indexes", "base_versions") ?: return@mapNotNull null
        val fileIndexVersions = it.metadata.mapFromPath("indexes", "file_index_versions") ?: return@mapNotNull null
        val stubIndexVersions = it.metadata.mapFromPath("indexes", "stub_index_versions") ?: return@mapNotNull null
        IndexInfrastructureVersion(baseVersions, fileIndexVersions, stubIndexVersions) to it
      }.toMap()

    val best = ourVersion.pickBestSuitableVersion(nodes.keys) ?: return null
    val match = nodes[best] ?: return null
    return match to best
  }

  fun writeIndexMetadata(indexName: String,
                         indexKind: String,
                         sourcesHash: String,
                         infrastructureVersion: IndexInfrastructureVersion): ByteArray {
    try {
      val om = ObjectMapper()

      val root = om.createObjectNode()

      root.put("metadata_version", METADATA_VERSION)

      root.putObject("sources").also { sources ->
        sources.put("os", IndexInfrastructureVersion.Os.getOs().osName)
        sources.put("os_name", SystemInfo.getOsNameAndVersion())
        sources.put("hash", sourcesHash)
        sources.put("kind", indexKind)
        sources.put("name", indexName)
      }

      root.putObject("environment").also { build ->
        build.put("os", IndexInfrastructureVersion.Os.getOs().osName)
        build.put("os_name", SystemInfo.getOsNameAndVersion())
        root.putObject("intellij").also { ij ->
          ij.put("product_code", ApplicationInfo.getInstance().build.productCode)
          ij.put("version", ApplicationInfo.getInstance().fullVersion)
          ij.put("build", ApplicationInfo.getInstance().build.toString())
        }
      }

      root.putObject("indexes").also { indexes ->
        indexes.put("weak_hash", infrastructureVersion.weakVersionHash)
        indexes.putObjectFromMap("base_versions", infrastructureVersion.baseIndexes)
        indexes.putObjectFromMap("file_index_versions", infrastructureVersion.fileBasedIndexVersions)
        indexes.putObjectFromMap("stub_index_versions", infrastructureVersion.stubIndexVersions)
      }

      return om.writerWithDefaultPrettyPrinter().writeValueAsBytes(root)
      //NOTE: should we include information about index sizes here too?
    } catch (t: Throwable) {
      throw RuntimeException("Failed to generate shared index metadata JSON. ${t.message}", t)
    }
  }

  private fun ObjectNode.putObjectFromMap(name: String, map: Map<String, String>) {
    putObject(name).also { obj ->
      map.entries.sortedBy { it.key.toLowerCase() }.forEach { (k, v) -> obj.put(k, v) }
    }
  }

  private fun ObjectNode.mapFromPath(vararg path: String): SortedMap<String, String>? {
    if (path.isEmpty()) return toMap()
    val first = path.first()
    val child = (get(first) as? ObjectNode) ?: return null
    return child.mapFromPath(*path.drop(1).toTypedArray())
  }

  private fun ObjectNode.toMap() : SortedMap<String, String>? {
    val result = HashMap<String, String>()
    for (key in this.fieldNames()) {
      if (result.containsKey(key)) return null
      val value = get(key)?.asText() ?: return null
      result[key] = value
    }
    return ImmutableSortedMap.copyOf(result)
  }
}
